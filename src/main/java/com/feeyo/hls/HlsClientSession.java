package com.feeyo.hls;


import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.m3u8.M3U8;
import com.feeyo.hls.m3u8.M3u8Builder;

import com.feeyo.hls.ts.TsSegment;
import com.feeyo.hls.ts.segmenter.AacTranscodingTsSegmenter;

/**
 * Hls live session for client
 *
 * @author xuwenfeng
 * @author zhuam
 */
public class HlsClientSession {
	
	private static Logger LOGGER = LoggerFactory.getLogger( HlsClientSession.class );
	
    private static final AtomicLong sessionIdGen = new AtomicLong(10000);
    
    private HlsLiveStream liveStream = null;

    //
    private String id;
    private long streamId;
    private long ctime;
    private long mtime;
    
    // m3u8
    private M3U8 m3u8 = null;
    private M3u8Builder m3u8Builder = new M3u8Builder( AacTranscodingTsSegmenter.TS_DURATION );
    
    private volatile long[] tsIndexs = null;		// m3u8 内对应的 TS列表
    
    public HlsClientSession(HlsLiveStream liveStream) {
        this.liveStream = liveStream;
        this.id = String.valueOf( sessionIdGen.incrementAndGet() );
        this.streamId = liveStream.getStreamId();
      
        long now = System.currentTimeMillis();
        this.ctime = now;
        this.mtime = now;
        
    }

    public long[] getTsIndexs() {
		return tsIndexs;
	}
    

	public M3U8 getM3u8File(String filename) throws Exception {
		
        boolean isTsModified = false;

    	/**
    	 * m3u8 content is following as ads valve is open
    	 * 
    	 * 1.ts 2.ts 3.ts
    	 * 2.ts 3.ts n.ts n+1.ts n+2.ts
    	 * 3.ts n.ts n+1.ts n+2.ts n+3.ts
    	 * n.ts n+1.its n+2.ts n+3.ts n+4.ts
    	 * 
    	 * @see https://tools.ietf.org/html/draft-pantos-http-live-streaming-13#section-6.3.3
    	 */
        
        if ( ctime == mtime && AdsMagr.isHasAds() ) {
        	tsIndexs = new long[] { 1, 2, 3 };
        	
        } else {
        	
        	long[] newTsIndexs = liveStream.fetchTsIndexs();
        	
        	if (tsIndexs != null && tsIndexs[0] < 4) {
    			
				if ( newTsIndexs != null && tsIndexs[tsIndexs.length - 1] < newTsIndexs[newTsIndexs.length - 1]) {
        			for(int i =0; i< tsIndexs.length -1; i++) {
        				tsIndexs[i] = tsIndexs[i+1];
        			}
        			
        			if(tsIndexs.length < 5) {
        				tsIndexs = Arrays.copyOf(tsIndexs, 5);
        				tsIndexs[2] = newTsIndexs[newTsIndexs.length-3];
        				tsIndexs[3] = newTsIndexs[newTsIndexs.length-2];
        				tsIndexs[4] = newTsIndexs[newTsIndexs.length-1];
        			}
        			
        			tsIndexs[tsIndexs.length - 1] = newTsIndexs[newTsIndexs.length - 1];
        			
    				isTsModified = true;
        		}
        		
			} else if ( tsIndexs == null && newTsIndexs != null) {
        		tsIndexs = newTsIndexs;
        		isTsModified = true;
        		
        	} else if ( tsIndexs != null && newTsIndexs != null ) {
        		
        		if ( tsIndexs[tsIndexs.length - 1] < newTsIndexs[newTsIndexs.length - 1] ) {
        			// 后往前移
        			for(int i =0; i< tsIndexs.length -1; i++) {
        				tsIndexs[i] = tsIndexs[i+1];
        			}
        			
        			tsIndexs[tsIndexs.length - 1] = tsIndexs[tsIndexs.length - 2] + 1;
    				isTsModified = true;
    			}
        	}
        	
        }
        
        this.mtime = System.currentTimeMillis();
    	
    	LOGGER.info("rquest filename={} " + ", tsIndexs=" + Arrays.toString( tsIndexs )  , filename );
    	
    	//
    	List<TsSegment> tsSegments = new LinkedList<TsSegment>();
    	if ( tsIndexs != null ) {
    		for(long tsIndex: tsIndexs) {
    			TsSegment tsSegment = liveStream.fetchTsSegment(tsIndex);
    			if ( tsSegment != null ) {
    				if ( tsSegment.isAds() )
    					 tsSegment.setDiscontinue(true);
    				tsSegments.add(tsSegment);
    			}
    		}
    	}
    	
    	long m3u8Seq = m3u8 == null ? 0 : m3u8.getSeq();
    	m3u8Seq++;
		m3u8 = m3u8Builder.generateM3u8( isTsModified ? m3u8Seq++ : m3u8Seq, tsSegments);
		
        LOGGER.info("response m3u8, {}", m3u8);
        
        return m3u8;
    }

    public String getId() {
        return id;
    }

    public long getStreamId() {
		return streamId;
	}

	public long getCtime() {
        return ctime;
    }

    public long getMtime() {
        return mtime;
    }

    public void setMtime(long mtime) {
        this.mtime = mtime;
    }
    
    @Override
    public String toString() {
    	
    	StringBuffer sb = new StringBuffer(80);
		sb.append("id=").append( id ).append(", ");
		sb.append("streamId=").append( streamId ).append(", ");
		sb.append("ctime=").append( ctime ).append(", ");
		sb.append("mtime=").append( mtime );
		return sb.toString();
    }   
  
}