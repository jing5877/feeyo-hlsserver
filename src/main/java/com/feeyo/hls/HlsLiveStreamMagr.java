package com.feeyo.hls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.audio.codec.Decoder;
import com.feeyo.audio.codec.PcmuDecoder;
import com.feeyo.net.udp.packet.V5Packet;
import com.feeyo.net.udp.packet.V5PacketType;

/**
 * HLS LIVE Stream 管理
 *
 */
public class HlsLiveStreamMagr {
	
	private static final Logger LOGGER = LoggerFactory.getLogger( HlsLiveStreamMagr.class );
	
    private static HlsLiveStreamMagr instance;
    
	private static final int SESSION_TIMEOUT_MS = 1000 * 60 * 2;			//
	private static final int LIVE_STREAM_TIMEOUT_MS = 1000 * 60 * 10;		//
	
    private static ExecutorService executor = Executors.newFixedThreadPool(10);
    private static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    // alias -> streamId 
	private static Map<String, Long> aliasToStreamIdCache = new ConcurrentHashMap<String, Long>();
    
    // streamId -> live stream
    private static Map<Long, HlsLiveStream> streamIdToLiveStreamCache = new ConcurrentHashMap<Long, HlsLiveStream>();
    
    // streamId -> volume control
    private static Map<Long, VolumeControl> streamIdToVolumeControlCache = new ConcurrentHashMap<Long, VolumeControl>();
    
    private HlsLiveStreamMagr() {}
    
    private static Object _lock = new Object();
    
    public static HlsLiveStreamMagr INSTANCE() {
    	if(instance == null ) {
    		synchronized (_lock) {
				if(instance == null)
					instance = new HlsLiveStreamMagr();
    		}
    	}
    	
        return instance;
    }
    
    
    public void startup() {
    	
		// delete expired session
    	scheduledExecutor.scheduleAtFixedRate( new Runnable() {

			@Override
			public void run() {
				
				List<Long> expiredStreamIds = new ArrayList<>();
            
            	long now = System.currentTimeMillis();
                for (HlsLiveStream hlsLiveStream : streamIdToLiveStreamCache.values()) {
                    
                	if (now - hlsLiveStream.getMtime() > LIVE_STREAM_TIMEOUT_MS) {
                        hlsLiveStream.close();
                        expiredStreamIds.add(hlsLiveStream.getStreamId());
                        
                    } else {
                        Map<String, HlsClientSession> clientSessions = hlsLiveStream.getAllClientSession();
                        for (String sessionId : clientSessions.keySet()) {
                            HlsClientSession clientSession = clientSessions.get(sessionId);
                            if ( now - clientSession.getMtime() > SESSION_TIMEOUT_MS ) {
                                hlsLiveStream.removeClientSessionById(sessionId);
                            }
                        }
                    }
                }

                for (Long tmpStreamId : expiredStreamIds) {
                    streamIdToLiveStreamCache.remove(tmpStreamId);
                    Iterator<Long> iter = aliasToStreamIdCache.values().iterator();
                    while(iter.hasNext()) {
                    	if(tmpStreamId == iter.next())
                    		iter.remove();
                    }
                    
                    streamIdToVolumeControlCache.remove(tmpStreamId);
                }
			}
    		
    	}, 10, 10, TimeUnit.SECONDS);
    
    }

    public void close() {
    	
        if (executor != null) {
            executor.shutdown();
        }
        
        if ( scheduledExecutor != null ) {
        	scheduledExecutor.shutdown();
        }
    }
    
    public HlsLiveStream getHlsLiveStreamByAlias(String alias) {
    	Long streamId = aliasToStreamIdCache.get(alias);
    	if ( streamId == null ) 
    		return null;
    	return streamIdToLiveStreamCache.get( streamId );
    }
    
    public Long getStreamIdByAlias(String alias) {
    	return aliasToStreamIdCache.get(alias);
    }
    
    public boolean updateHlsLiveStreamAliasNamesById(long streamId, List<String> newAliasNames) {
    	
    	HlsLiveStream liveStream = streamIdToLiveStreamCache.get( streamId );
    	if ( liveStream != null ) {
    		
    		for(String newName: newAliasNames) {
    			aliasToStreamIdCache.put(newName, streamId);
    		}
    		
    		List<String> oldAliasNames = liveStream.getAliasNames();
    		for(String oldName: oldAliasNames) {
    			aliasToStreamIdCache.remove( oldName );
    		}
    		
    		liveStream.setAliasNames( newAliasNames );
    		return true;
    	}
    	return false;
    }
    
    public void startHlsLiveStream(Long streamId, Integer streamType, List<String> aliasNames, 
    		Float sampleRate, Integer sampleSizeInBits, Integer channels, Integer fps) {
    	
    	HlsLiveStream liveStream = streamIdToLiveStreamCache.get(streamId);
		if ( liveStream == null ) {
			liveStream = new HlsLiveStream(streamId, streamType, aliasNames, sampleRate, sampleSizeInBits, channels, fps);
			streamIdToLiveStreamCache.put(streamId, liveStream);
			
			for(String aliasName : aliasNames)
				aliasToStreamIdCache.put(aliasName, streamId);
		}
		
		VolumeControl volumeCtl = streamIdToVolumeControlCache.get(streamId);
		if( volumeCtl == null ) 
			streamIdToVolumeControlCache.put( streamId, new VolumeControl());
    }
    
    public void closeHlsLiveStream(long streamId) {
    	
    	HlsLiveStream liveStream = streamIdToLiveStreamCache.remove(streamId);
		if ( liveStream != null ) {
			
			List<String> oldAliasNames = liveStream.getAliasNames();
    		for(String oldName: oldAliasNames) {
    			aliasToStreamIdCache.remove( oldName );
    		}
			
			liveStream.close();
			liveStream = null;
		}
		
		streamIdToVolumeControlCache.remove(streamId);
    }
    
    public void closeAllHlsLiveStream() {
    	//
    	aliasToStreamIdCache.clear();
    	
    	Iterator<Long> iter = streamIdToLiveStreamCache.keySet().iterator();
    	while( iter.hasNext() ) {
    		Long streamId = iter.next();
    		HlsLiveStream liveStream = streamIdToLiveStreamCache.remove(streamId);
    		if ( liveStream != null ) {
    			liveStream.close();
    			liveStream = null;
    		}
    	}
    	
    	streamIdToVolumeControlCache.clear();
    	
    }
    
	public void handleStream(final V5Packet packet) {

		executor.execute(new Runnable() {

			Decoder pcmuDecoder = new PcmuDecoder();

			@Override
			public void run() {
				long packetSender = packet.getPacketSender();
				byte packetType = packet.getPacketType();
				byte[] packetReserved = packet.getPacketReserved();

				HlsLiveStream liveStream = streamIdToLiveStreamCache.get(packetSender);
				if (liveStream != null) {

					boolean isPass = false;
					if ( liveStream.getStreamType() == HlsLiveStreamType.AAC && packetType == V5PacketType.AAC_STREAM ) {
						isPass = true;
						
					} else if ( liveStream.getStreamType() == HlsLiveStreamType.PCM && packetType == V5PacketType.PCM_STREAM ) {
						isPass = true;

					} else if (liveStream.getStreamType() == HlsLiveStreamType.H264 && packetType == V5PacketType.H264_STREAM ) {
						isPass = true;
						
					} else if (liveStream.getStreamType() == HlsLiveStreamType.YUV && packetType == V5PacketType.YUV422_STREAM ) {
						isPass = true;

					} else if (liveStream.getStreamType() == HlsLiveStreamType.AAC_H264_MIXED
							&& (packetType == V5PacketType.H264_STREAM || packetType == V5PacketType.AAC_STREAM)) {
						isPass = true;
					}

					if ( isPass ) {
						byte[] packetData = packet.getPacketData();
						byte[] reserved = packet.getPacketReserved();
						if (V5PacketType.PCM_STREAM == packet.getPacketType()) {
							packetData = pcmuDecoder.process(packet.getPacketData());
							VolumeControl volumeCtl = streamIdToVolumeControlCache.get(packetSender);
							if(volumeCtl != null)
								packetData = volumeCtl.autoControlVolume(packetData);
						}
						liveStream.addAvStream(packetType, packetReserved, packetData, reserved);
						
					} else {
						LOGGER.warn("livestream no pass err: streamId={}, streamType={}, packetType={}", liveStream.getStreamId(),
								liveStream.getStreamType(), packetType);
					}
					
				} else {
					LOGGER.warn("livestream not found err: packetSender={}, packetType={}", packetSender, packetType);
				}
			}
		});
	}
	
}