JAVAC="$JAVA_HOME/bin/javac"
export JAVAC
CLASSPATH=./:../Libs/rfa.jar
export CLASSPATH
EXAMPLE_PATH=./com/reuters/rfa/example
export EXAMPLE_PATH

rm -f `find . -name *.class`

$JAVAC $EXAMPLE_PATH/ansipage/*.java
$JAVAC $EXAMPLE_PATH/applet/*.java
$JAVAC $EXAMPLE_PATH/framework/chain/*.java
$JAVAC $EXAMPLE_PATH/framework/idn/*.java
$JAVAC $EXAMPLE_PATH/framework/prov/*.java
$JAVAC $EXAMPLE_PATH/framework/sub/*.java
$JAVAC $EXAMPLE_PATH/omm/domainServer/*.java
$JAVAC $EXAMPLE_PATH/omm/domainServer/marketbyorder/*.java
$JAVAC $EXAMPLE_PATH/omm/domainServer/marketbyprice/*.java
$JAVAC $EXAMPLE_PATH/omm/domainServer/marketmaker/*.java
$JAVAC $EXAMPLE_PATH/omm/domainServer/marketprice/*.java
$JAVAC $EXAMPLE_PATH/omm/domainServer/symbollist/*.java
$JAVAC $EXAMPLE_PATH/omm/idn/newsviewer/*.java
$JAVAC $EXAMPLE_PATH/omm/idn/tsconsole/*.java
$JAVAC $EXAMPLE_PATH/omm/idn/tstrend/*.java
$JAVAC $EXAMPLE_PATH/omm/multipleConsumers/*.java
$JAVAC $EXAMPLE_PATH/omm/dictionary/*.java
$JAVAC $EXAMPLE_PATH/omm/hybrid/*.java
$JAVAC $EXAMPLE_PATH/omm/hybrid/advanced/*.java
$JAVAC $EXAMPLE_PATH/omm/hybrid/simple/*.java
$JAVAC $EXAMPLE_PATH/omm/hybridni/*.java
$JAVAC $EXAMPLE_PATH/omm/itemgroups/*.java
$JAVAC $EXAMPLE_PATH/omm/warmstandbyprov/*.java
$JAVAC $EXAMPLE_PATH/omm/batchviewcons/*.java
$JAVAC $EXAMPLE_PATH/omm/batchviewprov/*.java
$JAVAC $EXAMPLE_PATH/omm/genericmsgcons/*.java
$JAVAC $EXAMPLE_PATH/omm/genericmsgprov/*.java
$JAVAC $EXAMPLE_PATH/omm/postingConsumer/*.java
$JAVAC $EXAMPLE_PATH/omm/postingProvider/*.java
$JAVAC $EXAMPLE_PATH/omm/cons/*.java
$JAVAC $EXAMPLE_PATH/omm/consPerf/*.java
$JAVAC $EXAMPLE_PATH/omm/prov/*.java
$JAVAC $EXAMPLE_PATH/omm/provni/*.java
$JAVAC $EXAMPLE_PATH/omm/sourcemirroringcons/*.java
$JAVAC $EXAMPLE_PATH/omm/sourcemirroringprov/*.java
$JAVAC $EXAMPLE_PATH/omm/symbollist/*.java
$JAVAC $EXAMPLE_PATH/omm/pagedisplay/*.java
$JAVAC $EXAMPLE_PATH/omm/gui/quotelist/*.java
$JAVAC $EXAMPLE_PATH/omm/gui/viewer/*.java
$JAVAC $EXAMPLE_PATH/omm/gui/orderbookdisplay/*.java
$JAVAC $EXAMPLE_PATH/omm/chain/cons/*.java
$JAVAC $EXAMPLE_PATH/omm/chain/prov/*.java
$JAVAC $EXAMPLE_PATH/utility/*.java
$JAVAC $EXAMPLE_PATH/utility/gui/*.java
$JAVAC $EXAMPLE_PATH/omm/privatestream/common/*.java
$JAVAC $EXAMPLE_PATH/omm/privatestream/pscons/*.java
$JAVAC $EXAMPLE_PATH/omm/privatestream/psprov/*.java
$JAVAC $EXAMPLE_PATH/omm/privatestream/psgmcons/*.java
$JAVAC $EXAMPLE_PATH/omm/privatestream/psgmprov/*.java
$JAVAC $EXAMPLE_PATH/quickstart/QuickStartConsumer/*.java
$JAVAC $EXAMPLE_PATH/quickstart/QuickStartProvider/*.java
$JAVAC $EXAMPLE_PATH/quickstart/QuickStartNIProvider/*.java
