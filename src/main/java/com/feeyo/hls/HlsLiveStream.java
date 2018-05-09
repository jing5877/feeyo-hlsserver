package com.feeyo.hls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.ads.AdsMagr;
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
    }
    
    
    //
    public void removeTimeoutSessionAndTsSegments(long now, int timeout) {
    	
    	// remove timeout client session
		for (String sessionId : clientSessions.keySet()) {
			HlsClientSession clientSession = clientSessions.get(sessionId);
			if (now - clientSession.getMtime() > timeout) {
				clientSessions.remove(sessionId);
				LOGGER.info("remove hls client: " + clientSession.toString() + " left: " + clientSessions.size());
			}
		}
    	
    	// get min TS Index
    	long minTsIndex = -1;
		
		for(HlsClientSession clientSession : clientSessions.values()) {
			long[] tsIndexs = clientSession.getOldTsIndexs();
			if ( tsIndexs != null ) {
				long tmpTsIndex = Longs.min(tsIndexs);
				if ( minTsIndex == -1 || minTsIndex > tmpTsIndex )  {
					minTsIndex = tmpTsIndex;
				} 
			}
		}
		
		// remove expire TS 
		for(Map.Entry<Long, TsSegment> entry:  tsSegments.entrySet() ) {
			long idx =  entry.getKey();
			TsSegment tsSegment = entry.getValue();
			
			if ( System.currentTimeMillis() - tsSegment.getLasttime() > timeout 
					|| (minTsIndex > idx) ) {
				tsSegments.remove( idx );
				LOGGER.info("remove ts= {}, minTsIndex= {} ", tsSegment, minTsIndex);
				
			} 
		}
    }
    
    // length= 3 ~ 5
    public long[] fetchTsIndexs() {
    	
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
    
    public TsSegment fetchTsSegmentByIndex(long index) {
    	if ( index < 0 )
    		return null;
    	
    	TsSegment tsSegment = null;
    	if ( index < 4 ) {
    		
    		String type = "audio";
    		switch( streamType ) {
        	case HlsLiveStreamType.YUV:
        	case HlsLiveStreamType.H264:
        		type = "video";
        		break;
        	case HlsLiveStreamType.AAC_H264_MIXED:
        		type = "mixed";
        		break;
        	}
    		
    		List<TsSegment> adTsSegments = adsMagr.getAdsTsSegments(type, sampleRate, sampleSizeInBits, channels, fps);
    		tsSegment = adTsSegments.get((int)index - 1);
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
	public synchronized void addAvStream(byte rawType, byte[] rawReserved, byte[] rawData, byte[] reserved) {
    	
    	this.mtime = System.currentTimeMillis();

    	if( tsSegmenter != null) {
	        byte[] tsData = tsSegmenter.getTsBuf( rawType, rawData, reserved );
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