"""Real subprocess streaming and durable callback ordering without GPU inference."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import httpx

from services.job_store import JobStore
from services.pipeline_worker import deliver_one, deliver_progress, process_one
from services.run_job import _run_with_progress
import services.pipeline_worker as worker


class ProgressTest(unittest.TestCase):
    def test_standalone_never_sends_callbacks(self):
        with tempfile.TemporaryDirectory() as temporary:
            with patch.dict('os.environ', {'TTOBAGI_STANDALONE': 'true', 'TTOBAGI_JOB_ROOT': temporary}), \
                 patch.object(worker, 'process_one', return_value=False) as process, \
                 patch.object(worker, 'deliver_one') as deliver, \
                 patch.object(worker.time, 'sleep', side_effect=KeyboardInterrupt):
                with self.assertRaises(KeyboardInterrupt):
                    worker.main()
                deliver.assert_not_called()
                self.assertIsNone(process.call_args.kwargs['on_progress'])

    def test_interruption_reaps_child(self):
        with tempfile.TemporaryDirectory() as temporary:
            children = []
            original = subprocess.Popen

            def launch(*args, **kwargs):
                child = original(*args, **kwargs)
                children.append(child)
                return child

            def interrupt(status):
                raise KeyboardInterrupt

            with (Path(temporary) / 'stdout.log').open('w') as output, \
                 patch('services.run_job.subprocess.Popen', side_effect=launch):
                with self.assertRaises(KeyboardInterrupt):
                    _run_with_progress([sys.executable, '-u', '-c',
                                        "import time; print('TTOBAGI_PROGRESS:PREPROCESSING', flush=True); time.sleep(30)"],
                                       output=output, on_progress=interrupt)
            self.assertIsNotNone(children[0].poll())

    def test_streams_before_child_finishes_and_preserves_terminal_delivery(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = JobStore(root)
            store.submit(4, 'same-input', {'masking': True, 'translation': False}, root / 'input.csv')
            received = []
            done = root / 'done'

            def backend(request):
                self.assertEqual(request.url.path, '/api/v1/dashboard/analyze/callback/4')
                self.assertEqual(request.headers['authorization'], 'Bearer secret')
                status = json.loads(request.content)['status']
                if status in {'PREPROCESSING', 'ANALYZING'}:
                    self.assertFalse(done.exists())
                    self.assertEqual(store.get(4)['state'], 'RUNNING')
                    self.assertIsNone(store.get(4)['payload'])
                received.append(status)
                return httpx.Response(200)

            with httpx.Client(transport=httpx.MockTransport(backend)) as client:
                def runner(source, job, **options):
                    script = root / 'fake.py'
                    script.write_text(
                        "import time\nfrom pathlib import Path\n"
                        "print('TTOBAGI_PROGRESS:PREPROCESSING', flush=True)\ntime.sleep(.3)\n"
                        "print('TTOBAGI_PROGRESS:ANALYZING', flush=True)\ntime.sleep(.3)\n"
                        "Path(__file__).with_name('done').touch()\n", encoding='utf-8')
                    with (root / 'stdout.log').open('w') as output:
                        result = _run_with_progress([sys.executable, '-u', str(script)], output=output,
                                                    on_progress=options['on_progress'])
                    self.assertEqual(result.returncode, 0)
                    self.assertEqual(received, ['PREPROCESSING', 'ANALYZING'])
                    return 7  # A subsequent failure must still produce FAILED.

                progress = lambda job, status: deliver_progress(store, job, status, 'https://be.example', 'secret', client)
                process_one(store, runner, on_progress=progress)
                progress(4, 'PREPROCESSING')  # Never regress or send after terminal.
                deliver_one(store, 'https://be.example', 'secret', client)
                self.assertEqual(received, ['PREPROCESSING', 'ANALYZING', 'FAILED'])
                self.assertEqual(store.get(4)['delivery'], 'DELIVERED')

    def test_timeout_duplicate_reverse_and_restart_do_not_retry(self):
        with tempfile.TemporaryDirectory() as temporary:
            store = JobStore(temporary)
            store.submit(1, 'fingerprint', {}, 'input')
            store.claim()
            calls = []

            def timeout(request):
                calls.append(request)
                raise httpx.ReadTimeout('ambiguous')

            with httpx.Client(transport=httpx.MockTransport(timeout)) as client:
                for status in ['PREPROCESSING', 'PREPROCESSING', 'ANALYZING', 'PREPROCESSING']:
                    deliver_progress(store, 1, status, 'https://be.example', 'secret', client)
                restarted = JobStore(temporary)
                restarted.recover_callbacks()
                deliver_progress(restarted, 1, 'ANALYZING', 'https://be.example', 'secret', client)
            self.assertEqual(len(calls), 2)
            self.assertEqual(store.get(1)['state'], 'RUNNING')
            self.assertEqual(store.get(1)['delivery'], 'NONE')
            with store.connect() as db:
                self.assertEqual(db.execute('SELECT DISTINCT delivery FROM progress_callbacks').fetchall(), [('UNKNOWN',)])

    def test_progress_handler_error_does_not_kill_subprocess(self):
        with tempfile.TemporaryDirectory() as temporary:
            with (Path(temporary) / 'stdout.log').open('w') as output:
                def broken(status):
                    raise RuntimeError('transport failed')
                result = _run_with_progress(
                    [sys.executable, '-u', '-c', "print('TTOBAGI_PROGRESS:PREPROCESSING', flush=True)"],
                    output=output, on_progress=broken)
            self.assertEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main()
