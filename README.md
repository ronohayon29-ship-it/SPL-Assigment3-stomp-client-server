# SPL Assignment 3 – STOMP Client-Server Messaging System

This project was developed as part of the **Systems Programming Laboratory (SPL)** course.

It implements a full **STOMP-based messaging system** with a multi-language architecture:

- A **Java server** that supports both **TPC** and **Reactor** concurrency models
- A **C++ client** that communicates with the server over TCP
- A **Python SQL service** used for data-related functionality

The project demonstrates networking, concurrency, protocol implementation, and client-server system design.

---

## Project Structure

```
client/     C++ client implementation
server/     Java server implementation
data/       Python SQL server and data files
```

---

## Technologies

- Java
- C++
- Python
- TCP sockets
- STOMP protocol
- Multithreading
- Reactor / TPC server models
- Maven
- Makefile

---

## How to Run

### 1. Run the Python SQL server

```bash
cd data
python3 sql_server.py
```

### 2. Run the Java server

```bash
cd server
mvn clean
mvn compile
```

#### Reactor mode

```bash
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 reactor 8"
```

#### TPC mode

```bash
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 tpc"
```

### 3. Build and run the C++ client

```bash
cd client
make clean
make
./bin/StompWCIClient
```

---

## Example Client Commands

```text
login 127.0.0.1:7777 ron pass
login 127.0.0.1:7777 liad pass

join Germany_Japan
report data/events1.json
summary Germany_Japan liad out.txt
```

---

## Main Features

- STOMP protocol encoding and decoding
- Topic subscription and reporting flow
- Concurrent server-side message handling
- Support for both **Reactor** and **TPC** architectures
- Cross-language integration between Java, C++, and Python components

---

## Authors

- Ron Ohayon
- Liad Levi
