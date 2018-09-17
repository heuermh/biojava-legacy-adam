# biojava-legacy-adam

BioJava 1.x (biojava-legacy) and ADAM integration. 

### Hacking biojava-legacy-adam

Install

 * JDK 1.8 or later, http://openjdk.java.net
 * Apache Maven 3.3.9 or later, http://maven.apache.org
 * Apache Spark 2.2.1 or later, http://spark.apache.org


To build

    $ mvn install


To run

```
$ spark-shell \
    --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
    --conf spark.kryo.registrator=org.biojava.adam.BiojavaKryoRegistrator \
    --jars target/biojava-legacy-adam-1.9.2-SNAPSHOT.jar

Welcome to
      ____              __
     / __/__  ___ _____/ /__
    _\ \/ _ \/ _ `/ __/  '_/
   /___/ .__/\_,_/_/ /_/\_\   version 2.2.1
      /_/

Using Scala version 2.11.8 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0_111)
Type in expressions to have them evaluated.
Type :help for more information.

scala> import org.biojava.adam.BiojavaAdamContext
import org.biojava.adam.BiojavaAdamContext

scala> val biojavaContext = new BiojavaAdamContext(sc)
biojavaContext: org.biojava.adam.BiojavaAdamContext = org.biojava.adam.BiojavaAdamContext@1e041848
```
