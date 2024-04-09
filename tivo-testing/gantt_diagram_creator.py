# Syntax: python3 generate_gantt_diagram.py <input_file.json> <gantt_chart_title> 

import sys, json, copy
from statistics import mean, stdev


def GetChannelName( url ) :
    splitPoint = "/transmux"
    try : return url.split( splitPoint )[0].split( '/' )[ -1 ]
    except : return None


def SampleStatsMs( samples ) :

    def generateStats( samples ) :
        return { 'min' : int( min   ( samples ) * 1000 ) if len( samples ) > 0 else 0,
                 'max' : int( max   ( samples ) * 1000 ) if len( samples ) > 0 else 0,
                 'avg' : int( mean  ( samples ) * 1000 ) if len( samples ) > 0 else 0,
                 'sdv' : int( stdev ( samples ) * 1000 ) if len( samples ) > 1 else 0 }

    try : return ( generateStats( [ x[0] for x in samples if x != None and x[0] != None ] ),
                   generateStats( [ x[1] for x in samples if x != None and x[1] != None ] ) )
    except : return None


def RejectPolicy( rec ) :

    # Channel change beyond 5 sec will be thrown away
    try : howLong = rec.get( 'isPlaying' )[ 1 ]
    except : return False

    if howLong > 5.0 :
        print( "Channel change took {} sec - skipping!".format( howLong ) )
        return False

    return True


if len( sys.argv ) != 3 :
    print( "Syntax: python3 {} <input_file.json> <gantt_chart_title>".format( sys.argv[0] ) )
    exit( 1 )

outputFileName = sys.argv[2] + '.gantt'
print( "Generating {} diagram...".format( outputFileName ) )

try : inputFile = open( sys.argv[1] )
except :

    print( "Can't open {} file!".format( sys.argv[1] ) )
    exit( 1 )

try : jsonRoot = json.load( inputFile )
except :

    print( "Can't load {} file!".format( sys.argv[1] ) )
    exit( 1 )

metric = \
{        \
  'isPlaying'                : [], \
  'renderedFirstFrame'       : [], \
  'keyRequest'               : [], \
  'drmSessionAcquired'       : [], \
  'audioDecoderInitialized'  : [], \
  'videoDecoderInitialized'  : [], \
  'loadCompletedPlaylist-F'  : [], \
  'loadCompletedPlaylist-L'  : [], \
  'loadCompletedMedia-A'     : [], \
  'loadCompletedMedia-V'     : []  \
}

metricsByChannel = {}

for iRec in jsonRoot :

    if not RejectPolicy( iRec ) : continue

    title = "{}  [dev={}/{} exo={} encr={} chpr={} tunl={}]". \
        format( GetChannelName( iRec[ 'channelUrl' ] ).upper(),
                iRec[ 'deviceType'    ],
                iRec[ 'deviceVer'     ],
                iRec[ 'playerVer'     ],
                iRec[ 'keyRequest'    ] != None,
                iRec[ 'chunklessUsed' ] == True,
                iRec[ 'tunnelingUsed' ] == True )

    mx = metricsByChannel.get( title )
    if mx == None :

        mx = copy.deepcopy( metric )
        metricsByChannel[ title ] = mx

    for k in metric.keys() :

        try :
            if k[ : -2 ] == 'loadCompletedPlaylist' :

                v = iRec.get( k[ : -2 ] )
                if v == None or len( v ) == 0 : continue

                if k[ -1 ] == 'F' : mx[ k ].append( list( v.values() )[  0 ] )
                if k[ -1 ] == 'L' : mx[ k ].append( list( v.values() )[ -1 ] )

            elif k[ : -2 ] == 'loadCompletedMedia' :

                v = iRec.get( k[ : -2 ] )
                if v == None or len( v ) == 0 : continue

                if k[ -1 ] == 'A' : mx[ k ].append( list( v.values() )[ 0 ] )
                if k[ -1 ] == 'V' : mx[ k ].append( list( v.values() )[ 1 ] )

            else :

                v = iRec.get( k )
                if v == None or len( v ) == 0 : continue
                mx[ k ].append( v )

        except :

            print( "Sample grouping error!" )
            continue

ganttOutput = \
[ \
  "gantt",
  "  title {}".format( sys.argv[2] ),
  "  dateFormat X",
  "  axisFormat %s"
]

for section, mx in metricsByChannel.items() :

    sectionData = []
    for k, v in mx.items() :

        if len( v ) == 0 : continue

        stats = SampleStatsMs( v )
        if stats == None :
            print( "Processing error!" )
            exit( 1 )

        if stats[ 0 ][ 'avg' ] == 0 :

            sectionData.append( "  {} [b={} w={} av={} dv={}] : milestone, {}".format(
                k,
                stats[ 1 ][ 'min' ],
                stats[ 1 ][ 'max' ],
                stats[ 1 ][ 'avg' ],
                stats[ 1 ][ 'sdv' ],
                stats[ 1 ][ 'avg' ] ) )
        else :

            sectionData.append( "  {} -- b={} w={} av={} dv={} -- b={} w={} av={} dv={} : done, {}, {}".format(
                k,
                stats[ 0 ][ 'min' ],
                stats[ 0 ][ 'max' ],
                stats[ 0 ][ 'avg' ],
                stats[ 0 ][ 'sdv' ],

                stats[ 1 ][ 'min' ],
                stats[ 1 ][ 'max' ],
                stats[ 1 ][ 'avg' ],
                stats[ 1 ][ 'sdv' ],

                stats[ 0 ][ 'avg' ],
                stats[ 0 ][ 'avg' ] + stats[ 1 ][ 'avg' ] ) )

    ganttOutput += [ "" ]
    ganttOutput.append( "  {} ({}) : crit, 0, {}". \
                        format( section, len( mx[ 'isPlaying' ] ),3000 ) )
    ganttOutput += sectionData

try : outputFile = open( outputFileName, "w+" )
except :

    print( "Can't create {} file!".format( outputFileName ) )
    exit( 1 )

try :
    for l in ganttOutput : print( l, file=outputFile )
    print( "", file=outputFile )
except :

    print( "Can't write {} file!".format( outputFileName ) )
    exit( 1 )

print( "Diagram {} complete!".format( outputFileName ) )
