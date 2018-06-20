#!/bin/sh

export JAVA_HOME=/usr/local/software/jdk1.7.0_71
#export JAVA_HOME=/usr/src/java/jdk1.7.0_51

#check JAVA_HOME & java
noJavaHome=false
if [ -z "$JAVA_HOME" ] ; then
    noJavaHome=true
fi
if [ ! -e "$JAVA_HOME/bin/java" ] ; then
    noJavaHome=true
fi
if $noJavaHome ; then
    echo
    echo "Error: JAVA_HOME environment variable is not set."
    echo
    exit 1
fi
#==============================================================================

#set HOME
CURR_DIR=`pwd`
cd `dirname "$0"`/..
FEEYO_HOME=`pwd`
cd $CURR_DIR
if [ -z "$FEEYO_HOME" ] ; then
    echo
    echo "Error: FEEYO_HOME environment variable is not defined correctly."
    echo
    exit 1
fi
#==============================================================================

#set CLASSPATH
FEEYO_CLASSPATH="$FEEYO_HOME/conf:$FEEYO_HOME/lib/classes"
for i in "$FEEYO_HOME"/lib/*.jar
do
    FEEYO_CLASSPATH="$FEEYO_CLASSPATH:$i"
done
#==============================================================================

#set JAVA_OPTS & PID_FILE & RUN_CMD
                                                          
SERVICE_NAME="hlsserver" 
logFile="$FEEYO_HOME/logs/console.log"
pidFile="$FEEYO_HOME/bin/run.pid"   


#===========================================================================================
# JVM Configuration
#===========================================================================================
JAVA_OPTS="${JAVA_OPTS} -server -Xms2g -Xmx2g -Xmn1g -XX:PermSize=128m -XX:MaxPermSize=128m -XX:SurvivorRatio=18 -XX:MaxDirectMemorySize=1g"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseConcMarkSweepGC -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseCMSCompactAtFullCollection -XX:CMSFullGCsBeforeCompaction=5"
JAVA_OPTS="${JAVA_OPTS} -XX:+CMSParallelRemarkEnabled -XX:CMSMaxAbortablePrecleanTime=5000"
JAVA_OPTS="${JAVA_OPTS} -XX:+DisableExplicitGC -XX:-UseParNewGC"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$FEEYO_HOME/logs/hlsserver_java.hprof"

#JAVA_OPTS="${JAVA_OPTS} -verbose:gc -Xloggc:$FEEYO_HOME/logs/hlsserver_gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps"
#JAVA_OPTS="${JAVA_OPTS} -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=100M"

#JAVA_OPTS="${JAVA_OPTS} -Xdebug -Xrunjdwp:transport=dt_socket,address=9555,server=y,suspend=n"

    
JAVA_RUN="$JAVA_HOME/bin/java"
JAVA_RUN="$JAVA_RUN -DFEEYO_HOME=\"$FEEYO_HOME\""
JAVA_RUN="$JAVA_RUN -classpath \"$FEEYO_CLASSPATH\""
JAVA_RUN="$JAVA_RUN $JAVA_OPTS"
JAVA_RUN="$JAVA_RUN com.feeyo.HlsServer $@"
JAVA_RUN="$JAVA_RUN >> \"$logFile\" 2>&1 & echo \$! >$pidFile"
hls_pid=`ps -ef | grep 'hlsserver' | grep java | awk '{print $2}'`

start(){
        if [ -z $hls_pid ];then
          #echo $JAVA_RUN
          eval $JAVA_RUN
          echo "$SERVICE_NAME started ..."
        else 
          echo "hlsserver is exit,please check..."
        fi
}
stop(){
        kill $hls_pid >/dev/null 2>&1
        sleep 2
        hls_pid=`ps -ef | grep 'hlsserver' | grep java | awk '{print $2}'`
        if [ -z $hls_pid ];then
          echo "stop success!!!"
        else
          kill -9 $hls_pid >/dev/null 2>&1
          echo "stop success!!!"
        fi
}
case $1 in
    start)
    start
    ;;
    stop)
    stop
    ;;
    restart)
    stop
    start
    ;;
esac
