from pathlib import Path
Path("/home/iot-solutions/ispf-tck/mosquitto.conf").write_text(
    "listener 1883 0.0.0.0\nallow_anonymous true\npersistence false\nsys_interval 1\n"
)
print("wrote", Path("/home/iot-solutions/ispf-tck/mosquitto.conf").stat().st_size)
