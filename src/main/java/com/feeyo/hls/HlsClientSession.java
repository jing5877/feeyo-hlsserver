package com.feeyo.hls;


import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.ads.AdsMagr;
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
    
    private volatile long[] oldTsIndexs = null;		// m3u8 内对应的 TS列表
    
    public HlsClientSession(HlsLiveStream liveStream) {
        this.liveStream = liveStream;
        this.id = String.valueOf( sessionIdGen.incrementAndGet() );
        this.streamId = liveStream.getStreamId();
      
        long now = System.currentTimeMillis();
        this.ctime = now;
        this.mtime = now;
        
    }

    public long[] getOldTsIndexs() {
		return oldTsIndexs;
	}
    

	public M3U8 getM3u8File(String filename) throws Exception {
		
        boolean isTsModified = false;

    	/**
    	 * @see https://tools.ietf.org/html/draft-pantos-http-live-streaming-13#section-6.3.3
    	 */
        
        if ( ctime == mtime && AdsMagr.isHasAds() ) {
        	
        	oldTsIndexs = new long[] { 1, 2, 3 };
        	
        } else {
        	
        	// 无嵌入式广告
        	if ( oldTsIndexs == null ) {
        		
        		long[] newTsIndexs = liveStream.fetchTsIndexs();
        		if ( newTsIndexs != null ) {
        			int tsNum = Math.min( newTsIndexs.length, 5);
        			
        			long[] tmpIndexs = new long[ tsNum ];
        			System.arraycopy(newTsIndexs, newTsIndexs.length - tsNum, tmpIndexs, 0, tsNum);
        			
        			oldTsIndexs = tmpIndexs;
        		    isTsModified = true;
        		}
        		
        	} else {
        		
        		//
        		long[] newTsIndexs = liveStream.fetchTsIndexs();
        		if ( newTsIndexs != null ) {

        			long lastOldIndex = oldTsIndexs[oldTsIndexs.length - 1];
        			long lastNewIndex = newTsIndexs[newTsIndexs.length - 1];
        			
        			int p1 = (int) ( lastNewIndex - lastOldIndex );
        			int p2 = ( 5 - oldTsIndexs.length );
        		
        			// 空间长度不足5
        			if ( p2 > 0 ) {
        				
        				// 存在新的 ts
        				if ( p1 > 0 ) {
        					
        					/*
        					  123,  4      --> 234
        					  123,  45     --> 2345
        					  123,  456    --> 23456
        					  123,  4567   --> 23456 
        					  
        					  234,  4567   --> 34567
        					  2345, 45678  --> 34567
        					 */
        					
        					// 最小的填充size
        					int padLength = Math.min(p1, p2);
        					
        					long[] tmpIndexs1 = new long[ oldTsIndexs.length + padLength];
        					System.arraycopy(oldTsIndexs, 0, tmpIndexs1, 0, oldTsIndexs.length);
        					
        					long tmpOldLastIndex = lastOldIndex;
        					for(int i = oldTsIndexs.length; i < tmpIndexs1.length; i++ ) {
        						tmpOldLastIndex++;
        						tmpIndexs1[i] = tmpOldLastIndex; 
        					}
        					
        					// 前移
        					long[] tmpIndexs2 = new long[ tmpIndexs1.length - 1 ];
        					System.arraycopy(tmpIndexs1, 1, tmpIndexs2, 0, tmpIndexs1.length - 1);
        					
        					long lastTmpIndex2 = tmpIndexs2[tmpIndexs2.length-1];
        					
        					
        					// 追加最新的 ts index
        					if ( lastNewIndex > lastTmpIndex2 ) {
        						long[] tmpIndexs3 = new long[ tmpIndexs1.length ];
        						System.arraycopy(tmpIndexs2, 0, tmpIndexs3, 0, tmpIndexs2.length);
        						tmpIndexs3[ tmpIndexs3.length - 1 ] = lastTmpIndex2 + 1;
        						
        						oldTsIndexs = tmpIndexs3;
        						
        					} else {
        						oldTsIndexs = tmpIndexs2;
        					}
        					
        					isTsModified = true;
        				}
  
        			} else {       				
        				//
        				if ( p1 > 0 ) {
        					long[] tmpIndexs = new long[ 5 ];
        					System.arraycopy(oldTsIndexs, 1, tmpIndexs, 0, oldTsIndexs.length - 1);	// 前移
        					tmpIndexs[4] = lastOldIndex + 1;											// 追加
        					
        					oldTsIndexs = tmpIndexs;
        					isTsModified = true;
        				}
        			}
        			        			
        		}
        	}
        	
        }
        
        this.mtime = System.currentTimeMillis();
    	
    	LOGGER.info("rquest filename={} " + ", tsIndexs=" + Arrays.toString( oldTsIndexs )  , filename );
    	
    	//
    	List<TsSegment> tsSegments = new LinkedList<TsSegment>();
    	if ( oldTsIndexs != null ) {
    		for(long tsIndex: oldTsIndexs) {
    			TsSegment tsSegment = liveStream.fetchTsSegmentByIndex(tsIndex);
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