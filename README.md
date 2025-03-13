# DragonSync-Android

[![Android CI](https://github.com/lukeswitz/DragonSync-Android/actions/workflows/android.yml/badge.svg)](https://github.com/lukeswitz/DragonSync-Android/actions/workflows/android.yml)

## Requirements

> [!NOTE]
> DragonSync will soon use onboard WiFi and BT.

*For the time being, we rely on a more powerful hardware stack:*

### Hardware
Works with WarDragon Pro right out of the box, otherwise follow the below:

**Required:**
- Catsniffer or Sonoff flashed with the latest Sniffle FW (Bluetooth)
- WiFi adapter or ESP32 flashed with the WiFI RID FW (WiFi)

**Optional:**
- GPS Unit (Status and spoof detection)
- ANT SDR E200 (DJI decoding of Ocusync and others)

### Software
*We need to install a few scripts that the app will run on top of.*

## Quick Start

### 1. Grab the Code

- [DroneID](https://github.com/alphafox02/DroneID) (Required)

    ```bash
    git clone https://github.com/alphafox02/DroneID
    cd DroneID
    git submodule init
    git submodule update
    ```
    
- [DragonSync](https://github.com/alphafox02/DragonSync) (CoT TAK/ATAK & Multicast)
   
  ```bash
  git clone https://github.com/alphafox02/DragonSync
  cd DragonSync

  # Install dependencies

  pip3 install -r requirements.txt
  sudo apt update && sudo apt install lm-sensors gpsd gpsd-clients
  ```
    
### 2. Start BT Sniffing
```python
python3 sniffle/python_cli/sniff_receiver.py -l -e -a -z
```

> *Where `sniffle` is your submodule folder in the `DroneID` repo from above*
> 
> *For Sonoff, the baud `-b 2000000` flag is needed*

### 3. Start WiFi Sniffing

**If using an external adapter (not esp32)**
```python
./wifi_receiver.py --interface wlan0 -z --zmqsetting 127.0.0.1:4223
```

> *You may need to put your adapter into monitor mode manually if the `wifi_receiver` cannot*
>
> *Replace `wlan0` with your interface name*

### 4. Start Decoding

- Option A: With WiFi Adapter

```python
python3 zmq_decoder.py -z --zmqsetting 0.0.0.0:4224 --zmqclients 127.0.0.1:4222,127.0.0.1:4223
```

- Option B: With ESP32 WiFi RID & ANT SDR

```python
python3 zmq_decoder.py -z --uart /dev/esp0 --dji 127.0.0.1:4221 --zmqsetting 0.0.0.0:4224 --zmqclients 127.0.0.1:4222
```

> *Where `/dev/esp0` is your ESP32 port*
  
### 4. Start System Monitoring (Optional)

```python
python3 wardragon_monitor.py --zmq_host 127.0.0.1 --zmq_port 4225 --interval 30
```

### 5. TAK/ATAK DragonSync (Optional: Limited Data)

- Without TAK (ZMQ & UDP)
```python
python3 dragonsync.py --zmq-host 0.0.0.0 --zmq-port 4225 --zmq-status-port 4224 --enable-multicast --tak-multicast-addr 224.0.0.1
```

- With TAK Server
```python
python3 dragonsync.py --zmq-host 0.0.0.0 --zmq-port 4224 --zmq-status-port 4225 --tak-host <tak_host> --tak-port <tak_port>
```

### 6. Start the App
- Open the app settings
- For ZMQ, enter the IP of the device running the scripts above
- For Multicast, use the default `224.0.0.1`.
- Toggle the switch on
- Monitor the dashboard and status view

> [!IMPORTANT]
> Multicast will be limited to what is contained in the CoT messages, while ZMQ will provide **all** available decoded data points.

## Overview

`// TODO`

## Features

`// TODO`
   
