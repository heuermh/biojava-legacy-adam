# biojava-legacy-adam

BioJava 1.x (biojava-legacy) and ADAM integration. 

### Hacking biojava-legacy-adam

Install

 * JDK 1.8 or later, http://openjdk.java.net
 * Apache Maven 3.8.5 or later, http://maven.apache.org
 * Apache Spark 3.2.0 or later, http://spark.apache.org
 * ADAM: Genomic Data System 0.37.0 or later, https://github.com/bigdatagenomics/adam

To build

    $ mvn install


To run

```
$ spark-shell \
    --conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
    --conf spark.kryo.registrator=org.biojava.adam.BiojavaKryoRegistrator \
    --jars target/biojava-adam-legacy-$VERSION.jar,$PATH_TO_ADAM_ASSEMBLY_JAR

Welcome to
      ____              __
     / __/__  ___ _____/ /__
    _\ \/ _ \/ _ `/ __/  '_/
   /___/ .__/\_,_/_/ /_/\_\   version 3.2.0
      /_/

Using Scala version 2.12.15 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0_191)
Type in expressions to have them evaluated.
Type :help for more information.

scala> import org.biojava.adam.BiojavaAdamContext
import org.biojava.adam.BiojavaAdamContext

scala> val biojavaContext = new BiojavaAdamContext(sc)
biojavaContext: org.biojava.adam.BiojavaAdamContext = org.biojava.adam.BiojavaAdamContext@1e041848
```
