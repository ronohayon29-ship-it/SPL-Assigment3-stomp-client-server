#!/usr/bin/env python3
"""
Basic Python Server for STOMP Assignment – Stage 3.3

IMPORTANT:
DO NOT CHANGE the server name or the basic protocol.
Students should EXTEND this server by implementing
the methods below.
"""

import os
import socket
import sys
import threading
import sqlite3


SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"  # DO NOT CHANGE!
DB_FILE = "stomp_server.db"              # DO NOT CHANGE!


def recv_null_terminated(sock: socket.socket) -> str:
    data = b""
    while True:
        chunk = sock.recv(1024)
        if not chunk:
            return ""
        data += chunk
        if b"\0" in data:
            msg, _ = data.split(b"\0", 1)
            return msg.decode("utf-8", errors="replace")


def init_database():
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    

    # users table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        username VARCHAR(20) PRIMARY KEY,
        password VARCHAR(20) NOT NULL,
        registration_date DATETIME NOT NULL
    )
    """)

    # login history table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS login_history (
        username VARCHAR(20) NOT NULL,
        login_time DATETIME NOT NULL,
        logout_time DATETIME
    )
    """)

    # file tracking table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS file_tracking (
        username VARCHAR(20) NOT NULL,
        filename VARCHAR(20) NOT NULL,
        upload_time DATETIME NOT NULL,
        game_channel VARCHAR(20) NOT NULL
    )
    """)

    conn.commit()
    conn.close()


def execute_sql_command(sql_command: str) -> str:
    try:
        conn = sqlite3.connect(DB_FILE)
        cursor = conn.cursor()

        cursor.execute(sql_command)
        conn.commit()

        conn.close()
        return "SUCCESS"

    except Exception as e:
        return f"ERROR: {str(e)}"


def execute_sql_query(sql_query: str) -> str:
    try:
        conn = sqlite3.connect(DB_FILE)
        cursor = conn.cursor()

        cursor.execute(sql_query)
        rows = cursor.fetchall()

        conn.close()
        return "SUCCESS" + "".join("|" + str(r) for r in rows)

    except Exception as e:
        return f"ERROR: {str(e)}"


def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")

    try:
        while True:
            message = recv_null_terminated(client_socket)
            if message == "":
                break

            msg_strip = message.strip()
            if msg_strip.lower().startswith("select"):
                result = execute_sql_query(msg_strip)
            else:
                result = execute_sql_command(msg_strip)

            # send result back (server expects something null-terminated)
            client_socket.sendall((result + "\0").encode("utf-8"))

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[{SERVER_NAME}] Client {addr} disconnected")


def start_server(host="127.0.0.1", port=7778):
    if os.path.exists(DB_FILE):
        os.remove(DB_FILE)
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    init_database()

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        print(f"[{SERVER_NAME}] Waiting for connections...")

        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()

    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass


if __name__ == "__main__":
    port = 7778
    if len(sys.argv) > 1:
        raw_port = sys.argv[1].strip()
        try:
            port = int(raw_port)
        except ValueError:
            print(f"Invalid port '{raw_port}', falling back to default {port}")

    start_server(port=port)
