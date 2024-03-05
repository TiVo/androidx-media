#--------------------------------------------------------------------------------------------------
#
# Channel tune performance script
# syntax: $ python3 tune_perf_data.py [input-log-file]
#
# Notes:
# - Use tune_perf_data.sh script if the log file is not Utf-8 encoded (not available in logcat
#   mode)
#
#--------------------------------------------------------------------------------------------------
import sys
import copy
import json
import dateutil.parser as dateparser

from datetime import date, time, datetime, timedelta
from enum     import Enum, auto
#--------------------------------------------------------------------------------------------------
class TpsStatus( Enum ) :
    TUNE      = auto()
    CONTINUE  = auto()
    ERROR     = auto()
#--------------------------------------------------------------------------------------------------
# TunePerfStats
#--------------------------------------------------------------------------------------------------
class TunePerfStats() :

    def __init__( self ) :

        # Line number in log file
        self.lineNo = 0

        # Tune time ref, the actual timestamp and delta from the tune request
        self.timeRef   = None
        self.timeStamp = None
        self.timeDelta = None

        # Indicates whether a/v has been added
        self.vidoSegmentAdded  = False
        self.audioSegmentAdded = False

        # Modules with perf data
        self.moduleMap = \
        {
            'ExoPlayerPlayer'           : self.ExoPlayerPlayerGroup,
            'HlsMediaPeriod'            : self.HlsMediaPeriod,
            'EventLogger'               : self.EventLogger,
            'executeProvisionRequest'   : self.ExecuteProvisionRequest,
            'executeKeyRequest'         : self.ExecuteKeyRequest
        }

        # Channel tune performance metrics
        self.tuneMetrics = \
        {
            "channelUrl"                : None,
            "dateTime"                  : None,
            "drmType"                   : None,
            "provisionRequest"          : None,
            "keyRequest"                : None,
            "chunklessUsed"             : None,
            "tunnelingUsed"             : None,
            "renderedFirstFrame"        : None,
            "isPlaying"                 : None,
            "loadCompletedPlaylist"     : None,
            "loadCompletedMedia"        : None
        }

        return

    #----------------------------------------------------------------------------------------------
    # Public methods
    #----------------------------------------------------------------------------------------------
    def ProcessLogLine( self, line ) :

        self.lineNo += 1

        # Groom & split
        replaceList = [ ',', '[', ']', '{', '}', '(', ')', ': ', ' :' ]
        for c in replaceList : line = line.replace( c, ' ' )
        fields = line.split()

        # Try to get log timestamp
        self.timeStamp = self._GetTimestamp( fields )
        if self.timeStamp == None : return TpsStatus.CONTINUE, None

        # Time delta from the last tune request
        self.timeDelta = ( self.timeStamp - self.timeRef ).total_seconds() \
            if self.timeRef != None else -1

        # Try to get module
        module = self._GetModule( fields )
        if module == None : return TpsStatus.CONTINUE, None

        if self.timeRef == None and module != 'ExoPlayerPlayer' :
            return TpsStatus.CONTINUE, None

        # Call module method
        return self.moduleMap[ module ]( fields )

    #----------------------------------------------------------------------------------------------
    def MaybeGetMetrics( self ) :

        if  self.tuneMetrics[ "channelUrl" ]         != None \
        and self.tuneMetrics[ "isPlaying" ]          != None \
        and self.tuneMetrics[ "renderedFirstFrame" ] != None \
        and self.vidoSegmentAdded \
        and self.audioSegmentAdded :

           _,data = self._TuneComplete()
           return data

        return None

    #----------------------------------------------------------------------------------------------
    # Module parsing methods
    #----------------------------------------------------------------------------------------------
    def ExoPlayerPlayerGroup( self, fields ) :

        status = TpsStatus.CONTINUE
        data   = None

        ### tunneling ###
        baseIndex = self._GetBaseIndex( 'Using', fields )
        if baseIndex != None :

            indexes = [ i + baseIndex for i in range( 1,4 ) ]
            value = self._GetPositionalValues( indexes, fields )

            if value == "tunneling mode on" : self.tuneMetrics[ "tunnelingUsed" ] = True
            return status, data

        ### tune request ###

        baseIndex = self._GetBaseIndex( 'play', fields )
        if baseIndex == None : return status, data

        # Channel tune begin, report actual metrics
        if self.tuneMetrics[ "channelUrl" ] != None :
            status, data = self._TuneComplete()

        # Save time ref
        self.timeRef = self.timeStamp
        self.tuneMetrics[ "dateTime" ] = self.timeStamp.isoformat()

        drmType= self._GetPositionalValues( [baseIndex + 1], fields )
        if drmType != None : self.tuneMetrics[ "drmType" ] = drmType[ 2 : ]

        channelUrl = self._GetPositionalValues( [baseIndex + 2], fields )
        if channelUrl == None :
            data = "Line {}: Can't get channel URL!".format( self.lineNo )
            return status, data

        # Add channel url
        self.tuneMetrics[ "channelUrl" ] = channelUrl
        return status, data

    #----------------------------------------------------------------------------------------------
    def HlsMediaPeriod( self, fields ) :

        baseIndex = self._GetBaseIndex( 'Chunkless', fields )
        if baseIndex == None : return TpsStatus.CONTINUE, None

        indexes = [ i + baseIndex for i in range( 1,4 ) ]
        value = self._GetPositionalValues( indexes, fields )

        if value == "preparation in use" : self.tuneMetrics[ "chunklessUsed" ] = True
        return TpsStatus.CONTINUE, None

    #----------------------------------------------------------------------------------------------
    def EventLogger( self, fields ) :

        message = None

        ### renderedFirstFrame ###

        baseIndex = self._GetBaseIndex( 'renderedFirstFrame', fields )
        if baseIndex != None :

            if self.tuneMetrics[ "renderedFirstFrame" ] == None :
                self.tuneMetrics[ "renderedFirstFrame" ] = self.timeDelta
            else :
                message = "Line {}: renderedFirstFrame already set!".format( self.lineNo )

            return TpsStatus.CONTINUE, message

        ### isPlaying ###

        baseIndex = self._GetBaseIndex( 'isPlaying', fields )
        if baseIndex != None :

            if self.tuneMetrics[ "isPlaying" ] != None : return TpsStatus.CONTINUE, message

            value = self._GetPositionalValues( [baseIndex + 6], fields )
            if value == None :
                message = "Line {}: Can't determine isPlaying state!".format( self.lineNo )
                return TpsStatus.CONTINUE, message

            if value == 'true' : self.tuneMetrics[ "isPlaying" ] = self.timeDelta

        def LoadCompletedCommon( event, fields ) :

            message = None

            # Create empty array if not present
            if self.tuneMetrics[ event ] == None : self.tuneMetrics[ event ] = {}

            # Uri
            uri = self._GetKeyValue( 'uri', fields )
            if uri == None :
                message = "Line {}: Can't determine uri!".format( self.lineNo )
                return None, None, message

            # Load duration
            value = self._GetKeyValue( 'load-duration', fields )
            if value != None :

                try : loadDuration = int( value[ : -2 ] )/1000
                except :
                    message = "Line {}: Can't determine load-duration!".format( self.lineNo )
                    return None, None, message

            return uri, loadDuration, message

        ### loadCompletedPlaylist ###

        baseIndex = self._GetBaseIndex( 'loadCompletedPlaylist', fields )
        if baseIndex != None :

            uri, loadDuration, message = LoadCompletedCommon( 'loadCompletedPlaylist', fields )
            if uri == None or loadDuration == None : return TpsStatus.CONTINUE, message

            # Only the first occurance is saved
            if uri in self.tuneMetrics[ 'loadCompletedPlaylist' ].keys() :
                return TpsStatus.CONTINUE, message

            # Add url and loadDuration
            self.tuneMetrics[ 'loadCompletedPlaylist' ][ uri ] = loadDuration

        ### loadCompletedMedia ###

        baseIndex = self._GetBaseIndex( 'loadCompletedMedia', fields )
        if baseIndex != None :

            uri,loadDuration, message = LoadCompletedCommon( 'loadCompletedMedia', fields )
            if uri == None or loadDuration == None : return TpsStatus.CONTINUE, message

            # Determin variant type
            isVideo = False

            value = self._GetKeyValue( 'trackId', fields )
            if value == None :
                message = "Line {}: Can't determine trackId!".format( self.lineNo )
                return TpsStatus.CONTINUE, message

            try : isVideo = value.isdigit()
            except :
                message = "Line {}: Can't determine if video!".format( self.lineNo )
                return TpsStatus.CONTINUE, message

            # Skip if the first video segment has been added
            if isVideo :

                if self.vidoSegmentAdded : return TpsStatus.CONTINUE, message
                else : self.vidoSegmentAdded = True

            # Skip if the first video segment has been added
            else :

                if self.audioSegmentAdded : return TpsStatus.CONTINUE, message
                else : self.audioSegmentAdded = True

            # Add url and loadDuration
            self.tuneMetrics[ 'loadCompletedMedia' ][ uri ] = loadDuration

        return TpsStatus.CONTINUE, message

    #----------------------------------------------------------------------------------------------
    def ExecuteProvisionRequest( self, fields ) :

        baseIndex = self._GetBaseIndex( 'completed', fields )
        if baseIndex == None : return TpsStatus.CONTINUE, None

        value = self._GetKeyValue( 'time', fields )
        if value != None :

            try : time = int( value ) / 1000
            except : return TpsStatus.CONTINUE, \
                   "Line {}: Can't get DRM provision request duration!".format( self.lineNo )

            self.tuneMetrics[ "provisionRequest" ] = time

        return TpsStatus.CONTINUE, None

    #----------------------------------------------------------------------------------------------
    def ExecuteKeyRequest( self, fields ) :

        baseIndex = self._GetBaseIndex( 'completed', fields )
        if baseIndex == None : return TpsStatus.CONTINUE, None

        value = self._GetKeyValue( 'time', fields )
        if value != None :

            try : time = int( value ) / 1000
            except : return TpsStatus.CONTINUE, \
                   "Line {}: Can't get DRM key request duration!".format( self.lineNo )

            self.tuneMetrics[ "keyRequest" ] = time

        return TpsStatus.CONTINUE, None

    #----------------------------------------------------------------------------------------------
    # Private methods
    #----------------------------------------------------------------------------------------------
    def _GetTimestamp( self, args ) :

        refDate = defaultDate = datetime( 2000, 1, 1, 0, 0, 0 )
        for arg in args :

            try : d = dateparser.parse( arg, default=defaultDate, fuzzy=True )
            except : continue
        
            if d.date() == refDate.date() : continue

            if d.date() != refDate.date() and d.time() != refDate.time() : return d
            else : defaultDate = d

        return None

    #----------------------------------------------------------------------------------------------
    def _GetModule( self, args ) :

        for m in self.moduleMap.keys() :

            # Try to find module index
            try : i = args.index( m )
            except : continue

            # Check if module name isn't too deep in the line
            if i > 10 : continue
            return m

        return None

    #----------------------------------------------------------------------------------------------
    def _GetBaseIndex( self, event, args ) :

        try : i = args.index( event )
        except : return None
        return i

    #----------------------------------------------------------------------------------------------
    def _GetKeyValue( self, key, args ) :

        try :
            i = args.index( key )
            return args[ i+1 ]
        except : pass
        return None

    #----------------------------------------------------------------------------------------------
    def _GetPositionalValues( self, offsets, args ) :

        values = ""
        for offset in offsets :

            try : values += args[ offset ] + ' '
            except : return None

        return values.rstrip() if len( values ) > 0 else None

    #----------------------------------------------------------------------------------------------
    def _TuneComplete( self ) :

        status = TpsStatus.TUNE
        data = copy.deepcopy( self.tuneMetrics )

        for k in self.tuneMetrics.keys() : self.tuneMetrics[ k ] = None
        self.vidoSegmentAdded = self.audioSegmentAdded = False
        self.timeRef = None

        return status, data

#--------------------------------------------------------------------------------------------------
# Main program
#--------------------------------------------------------------------------------------------------
if __name__ == "__main__" :

    if len( sys.argv ) == 1 : fd = sys.stdin
    else :
        try : fd = open( sys.argv[ 1 ], 'r' )
        except :
            print( "Can't open input file!", file=sys.stderr )
            exit( 1 )

    summary = []
    tps = TunePerfStats()

    while True :

        try : line = fd.readline()
        except : continue

        # Loop exit point
        if line == None or len( line ) == 0 : break

        # In real time mode check if metrics are complete and can be flushed
        if len( sys.argv ) == 1 :
            data = tps.MaybeGetMetrics()
            if data != None : print( json.dumps( data, indent=4 ) )

        # Process one line
        status, data = tps.ProcessLogLine( line )

        # Exit on error
        if status == TpsStatus.ERROR :
            print( data, file=sys.stderr )
            exit( 1 )

        # Log warning
        elif status == TpsStatus.CONTINUE :
            if data != None : print( data, file=sys.stderr )

        # Flush metrics upon now tume request
        elif status == TpsStatus.TUNE :

            if len( sys.argv ) == 1 : print( json.dumps( data, indent=4 ) )
            else : summary.append( data )

    # Update summary with the last bit (can be incomplete)
    if tps.tuneMetrics[ "channelUrl" ] != None : summary.append( tps.tuneMetrics )
    print( json.dumps( summary, indent=4 ) )
