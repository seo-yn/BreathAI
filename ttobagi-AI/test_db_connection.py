import pymysql

conn = pymysql.connect(
    host="127.0.0.1",
    port=3306,
    user="tbg_admin",
    password="tbg_pw_260501",
    database="ttobagi_db",
    charset="utf8mb4",
)

try:
    with conn.cursor() as cursor:
        cursor.execute("SELECT 1")
        result = cursor.fetchone()
        print("DB connection success:", result)
finally:
    conn.close()