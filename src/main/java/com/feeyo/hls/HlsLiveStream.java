package com.feeyo.hls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.ts.TsSegment;
import com.feeyo.hls.ts.segmenter.AacH264MixedTsSegmenter;
import com.feeyo.hls.ts.segmenter.AacTranscodingTsSegmenter;
import com.feeyo.hls.ts.segmenter.AacTsSegmenter;
import com.feeyo.hls.ts.segmenter.AbstractTsSegmenter;
import com.feeyo.hls.ts.segmenter.H264TranscodingTsSegmenter;
import com.feeyo.hls.ts.segmenter.H264TsSegmenter;
import com.google.common.primitives.Longs;


/**
 * HLS 在线流
 * 
 * cache the media for N ts segment, waiting for the client to access
 * 
 * @author tribf wangyamin@variflight.com
 * @author xuwenfeng
 * @author zhuam
 */
public class HlsLiveStream {
	
	private static Logger LOGGER = LoggerFactory.getLogger( HlsLiveStream.class );
    
    // id -> client session
    private Map<String, HlsClientSession> clientSessions = new ConcurrentHashMap<String, HlsClientSession>();
    
    private long mtime;
    
    private long streamId;
    private int streamType;
    
    // audio
    private float sampleRate;
    private int sampleSizeInBits;
    private int channels;
    
    // video
    private int fps;
    
    // asias
    private List<String> aliasNames;

    //
    private AbstractTsSegmenter tsSegmenter = null;

    private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    
    //
    private Map<Long, TsSegment> tsSegments = new ConcurrentHashMap<Long, TsSegment>(); 		
    private AtomicLong tsSegmentIndexGen = new AtomicLong(4);										// 4...
    private List<Long> tsSegmentIndexs = new ArrayList<Long>();
    private ReadWriteLock _lock = new ReentrantReadWriteLock();
    
    // ADS
    private static AdsMagr adsMagr;												// 1,2,3
    
    static {
    	if (adsMagr == null)
            adsMagr = new AdsMagr();
    }
    
    public HlsLiveStream(Long streamId, Integer streamType, List<String> aliasNames, 
    		Float sampleRate, Integer sampleSizeInBits, Integer channels, Integer fps) {
        
    	this.mtime = System.currentTimeMillis();
    	
    	this.streamId = streamId;
    	this.streamType = streamType;
    	
    	this.aliasNames = aliasNames;
    	
        this.sampleRate = sampleRate == null ? 8000F : sampleRate;
        this.sampleSizeInBits = sampleSizeInBits == null ? 16: sampleSizeInBits;
        this.channels = channels == null ? 1 : channels;
        this.fps = fps == null ? 25: fps;
        
        
        switch( streamType ) {
    	case HlsLiveStreamType.PCM:
    		tsSegmenter = new AacTranscodingTsSegmenter();
    		break;
    	case HlsLiveStreamType.AAC:
    		tsSegmenter = new AacTsSegmenter();
    		break;
    	case HlsLiveStreamType.YUV:
    		tsSegmenter = new H264TranscodingTsSegmenter();
    		break;
    	case HlsLiveStreamType.H264:
    		tsSegmenter = new H264TsSegmenter();
    		break;
    	case HlsLiveStreamType.AAC_H264_MIXED:
    		tsSegmenter = new AacH264MixedTsSegmenter();
    		break;
    	}
        
        tsSegmenter.initialize(sampleRate, sampleSizeInBits, channels, fps);

        //
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				
				long minTsIndex = -1;
				
				for(HlsClientSession clientSession : clientSessions.values()) {
					long[] tsIndexs = clientSession.getTsIndexs();
					if ( tsIndexs != null ) {
						long tmpTsIndex = Longs.min(tsIndexs);
						if ( minTsIndex == -1 || minTsIndex > tmpTsIndex )  {
							minTsIndex = tmpTsIndex;
						} 
					}
				}

				// delete
				if ( minTsIndex - 1 > 3 ) {
					
					for(Map.Entry<Long, TsSegment> entry:  tsSegments.entrySet() ) {
						long idx =  entry.getKey();
						TsSegment tsSegment = entry.getValue();
						if ( idx < minTsIndex && ( System.currentTimeMillis() - tsSegment.getLasttime() > 30 * 1000 ) ) {
							tsSegments.remove( idx );
							
							 LOGGER.info("remove ts= {}, minTsIndex= {} ", tsSegment, minTsIndex);
						}
					}
				}
			}
    		
    	}, 10, 10, TimeUnit.SECONDS);
    }
    
    
    // length= 3 ~ 5
    public long[] fetchTsIndexs() {
    	
    	//List<TsSegment> adTsSegments = hlsAdsWatchdog.getAdsTsSegments();
    	
    	if ( tsSegmentIndexs.size() < 3)  {
    		return null;
    	}
    	
    	//
    	try {
    		_lock.readLock().lock();
    		long[] indexs = Longs.toArray( tsSegmentIndexs );
    		return indexs;
    		
    	} finally {
    		_lock.readLock().unlock();
    	}
    }
    
    public TsSegment fetchTsSegment(long index) {
    	if ( index < 0 )
    		return null;
    	
    	TsSegment tsSegment = null;
    	if ( index < 4 ) {
    		List<TsSegment> adTsSegments = adsMagr.getAdsTsSegments();
    		tsSegment = adTsSegments.get( (int)index - 1);
    	} else {
    		tsSegment = tsSegments.get( index );
    	}
		
    	if ( tsSegment != null ) {
    		tsSegment.setLasttime(  System.currentTimeMillis() );
    	}
    	
    	return tsSegment;
	}
    
    //
    public HlsClientSession newClientSession() {
        
        HlsClientSession clientSession = new HlsClientSession(this);
        clientSessions.put(clientSession.getId(), clientSession);
        
        LOGGER.info("add client: " + clientSession.toString());
        return clientSession;
    }
    
    
    public void removeClientSessionById(String sessionId) {
    	
        HlsClientSession clientSession = clientSessions.remove(sessionId);
        if ( clientSession != null ) {
        	
        	LOGGER.info("remove hls client: " + clientSession.toString() + " left: " + clientSessions.size());
        }
    }
    
    public HlsClientSession getClientSessionsById(String id) {
    	HlsClientSession clientSession = clientSessions.get(id);
    	return clientSession;
    }
    
    public Map<String, HlsClientSession> getAllClientSession() {
        return clientSessions;
    }
    
    public void close() {
    	if ( clientSessions != null)
    		clientSessions.clear();
        
        if ( tsSegmenter != null)
        	tsSegmenter.close();
    }

    public long getMtime() {
        return mtime;
    }

    public long getStreamId() {
        return streamId;
    }
    
    public int getStreamType() {
		return streamType;
	}


	// 采样率
    public float getSampleRate() {
		return sampleRate;
	}

	public int getSampleSizeInBits() {
		return sampleSizeInBits;
	}

	public int getChannels() {
		return channels;
	}

	public int getFps() {
		return fps;
	}

	public List<String> getAliasNames() {
		return aliasNames;
	}

	public void setAliasNames(List<String> aliasNames) {
		this.aliasNames = aliasNames;
	}


	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	public synchronized void addAvStream(byte rawType, byte[] rawReserved, byte[] rawData) {
    	
    	this.mtime = System.currentTimeMillis();

    	if( tsSegmenter != null) {
    		
	        byte[] tsData = tsSegmenter.getTsBuf( rawType, rawData );
	        if ( tsData != null) {

	        	long tsIndex = tsSegmentIndexGen.getAndIncrement();
	            TsSegment tsSegment = new TsSegment(  tsIndex +".ts", tsData, tsSegmenter.getTsSegTime(), false);
	            tsSegments.put(tsIndex, tsSegment);
	            
	            LOGGER.info("add ts {} ", tsSegment);
	            
	            try {
	        		_lock.writeLock().lock();
		            if ( tsSegmentIndexs.size() >=5 ) {
		            	tsSegmentIndexs.remove(0);
		            }
		            tsSegmentIndexs.add( tsIndex );
	            } finally {
	            	_lock.writeLock().unlock();
	            }
	        }
    	}
    }

}