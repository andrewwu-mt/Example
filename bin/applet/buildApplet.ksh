mkdir classes
$JAVA_HOME/bin/javac -classpath ../:../../Libs/rfa.jar -d ./classes ../com/reuters/rfa/example/framework/sub/*.java ../com/reuters/rfa/example/omm/gui/quotelist/*.java ../com/reuters/rfa/example/utility/*.java ../com/reuters/rfa/example/utility/gui/*.java ../com/reuters/rfa/example/applet/*.java
cd classes
$JAVA_HOME/bin/jar -cf ../rfaj_applet_example.jar ./com/reuters/rfa/example/framework/sub/*.class ./com/reuters/rfa/example/omm/gui/quotelist/*.class ./com/reuters/rfa/example/utility/*.class ./com/reuters/rfa/example/utility/gui/*.class ./com/reuters/rfa/example/applet/*.class
cd ..
cp ../../Libs/rfa.jar ./signed_rfa.jar
$JAVA_HOME/bin/jarsigner -keystore ./applet_example.ks -storepass changeit rfaj_applet_example.jar mykey 
$JAVA_HOME/bin/jarsigner -keystore ./applet_example.ks -storepass changeit signed_rfa.jar mykey 
rm -Rf classes

