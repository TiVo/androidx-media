if [ -z $1 ] ; then
    echo "syntax: $ sh $0 <adb-log-file>"
    echo "        $ adb logcat | python3 tune_perf_metrics.py"
    exit 1
fi
TMPFILE=$(mktemp -p /tmp)
iconv -f ISO-8859-1 -t UTF-8//TRANSLIT $1 > $TMPFILE || { echo " re-encoding $1 failed!" ; exit 1; }
python3 tune_perf_metrics.py $TMPFILE
rm $TMPFILE
