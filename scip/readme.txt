NOTE!! This API is now many years old and entirely unmaintained. If someone wants to send me a URG-04LX to play with, I'd be happy to update the software.

SCIP 1.1 Java Interface for Hokuyo URG-04LX Laser Sensor
http://www.brianziman.com/r/post/stuff-200802201223
Last updated Dec 15, 2008.

The Autonomous Robotics Lab at George Mason University has attained 
several Hokuyo URG-04LX laser range-finding sensors that provide 
significantly better performance and accuracy than the less expensive 
sonar arrays.  The Hokuyo is generally controlled via a serial 
connection (or via a USB port, using the serial protocol).  The serial 
protocol is described by the SCIP 1.1 Specification, available from Hokuyo.

Because most robotics researchers are generally more concerned with testing
their algorithms than in getting bogged down with the hardware, I developed
a Java API that implements the serial protocol specification for the device.


See the API specification in the api/ folder.

This library requires GMU Professor Sean Luke's serial daemon.  A
working version is archived here as serialdaemon.c.gz

It should compile without complication on any reasonable system.

Run the serialdaemon like this:

$ ./serialdaemon -serial /dev/ttyACM0 -port 1701 -baud 115200 -debug

Then, run the demo like this:

$ java -cp jars/SCIP.jar:. com.brianziman.robotics.LaserVisualizer 1701

