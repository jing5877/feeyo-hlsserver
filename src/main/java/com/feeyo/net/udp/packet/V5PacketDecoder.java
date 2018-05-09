package com.feeyo.net.udp.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * 
 * @author zhuam
 *
 */
public class V5PacketDecoder {
	
	//private static final Logger LOGGER = LoggerFactory.getLogger( V5PacketDecoder.class );
	
	private static long TIMEOUT = 10 * 1000;
	
	// 支持拼接
	private static ConcurrentHashMap<Integer, V5PacketCacheItem> _cache = new ConcurrentHashMap<Integer, V5PacketCacheItem>();
	
	private static ScheduledExecutorService _executor = Executors.newScheduledThreadPool(1);
	
	static {
		_executor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {		
				//
				if ( _cache.isEmpty() ) 
					return;
				
				List<Integer> lossIds = new ArrayList<Integer>();
				for(Map.Entry<Integer, V5PacketCacheItem>  et : _cache.entrySet()) {
					
					int packetId = et.getKey();
					V5PacketCacheItem item = et.getValue();
					if ( item != null &&  (System.currentTimeMillis() - item.lastTime >= TIMEOUT) ) {
						lossIds.add( packetId );
					}
				}
				
				for(int id: lossIds) {
					_cache.remove( id );
				}
			}
			
		}, 5, 5, TimeUnit.SECONDS);
	}
	
	public void close() {
		
		if ( _executor != null ) {
			_executor.shutdown();
		}
		
		if ( _cache != null ) {
			_cache.clear();
		}
	}

	//
	public class V5PacketCacheItem {
		
		private List<V5Packet> packets = new ArrayList<V5Packet>();
		private long lastTime;
		
		public List<V5Packet> addPacket(V5Packet packet) {
			packets.add(packet);
			lastTime = System.currentTimeMillis();
			
			return packets;
		}

		public long getLastTime() {
			return lastTime;
		}
	}
	
	public V5Packet decode(byte[] buff) throws V5PacketErrorException {
		
		// 包头
		int idx = 0;
		if ( buff[idx] != 89 || buff[idx+1] != 89 ) {
			throw new V5PacketErrorException(" packet head error");
		}

		idx += 2;
		int packetSender = (int)ByteUtil.bytesToInt(buff[idx], buff[idx+1], buff[idx+2], buff[idx+3]);
		
		idx += 4;
		byte packetType = buff[idx];
		
		idx += 1;
		byte[] packetReserved = new byte[8];
		System.arraycopy(buff, idx, packetReserved, 0, 8);
				
		idx += 8;
		int packetId = (int)ByteUtil.bytesToInt(buff[idx], buff[idx+1], buff[idx+2], buff[idx+3]);
		
		idx += 4;
		int packetLength = (int) ByteUtil.bytesToInt(buff[idx], buff[idx+1], buff[idx+2], buff[idx+3]);
		
		idx += 4;
		int packetOffset = (int) ByteUtil.bytesToInt(buff[idx], buff[idx+1], buff[idx+2], buff[idx+3]);
		
		idx += 4;
		
		// 包体
		int length = buff.length - V5Packet.HEAD_LENGTH - V5Packet.TAIL_LENGTH;
		byte[] packetData = new byte[ length ];
		ByteUtil.copyBytes(buff, idx, packetData, 0, length);
		idx += length;
		
		// 包尾
		long crc = ByteUtil.bytesToLong(buff[idx], buff[idx+1], buff[idx+2], buff[idx+3], buff[idx+4], buff[idx+5], buff[idx+6], buff[idx+7]);
		
		// 做一下CRC 校验
		CRC32 oldCrc32 = new CRC32();
		oldCrc32.update( packetData, 0, packetData.length);
		if ( crc != oldCrc32.getValue() ) {
			throw new V5PacketErrorException("packet crc32 error");
		}
		
		//if ( LOGGER.isDebugEnabled() ) {
		//	LOGGER.debug( "packet_sender:" + packetSender + ", packet_id:" + packetId 
		//				+ ", packet_length:" + packetLength + ", packet_offset:" + packetOffset);
		//}
		
		
		V5Packet packet = new V5Packet(packetSender, packetType, packetReserved, packetId, packetLength, packetOffset, packetData, crc);
		
		// 分包的情况，需要考虑拼接
		if ( packet.isPartPacket() ) {
			
			V5PacketCacheItem item = _cache.get( packet.getPacketId() );
			if ( item == null ) {		
				item = new V5PacketCacheItem();
				_cache.put(packetId, item);		
			}	
			
			// 校验是否完整
			List<V5Packet> tmpPackets = item.addPacket(packet);
			if ( tmpPackets.size() > 1 ) {
				
				int tmpLength = 0;
				for (V5Packet p: tmpPackets ) {
					tmpLength += p.getPacketData().length;
				}
				
				// 完整包，拼装
				if ( tmpLength == packetLength ) {
					
					byte[] fullPacketData = new byte[ packetLength ];
					int offset = 0;
					for (V5Packet p: item.packets) {
						ByteUtil.copyBytes( p.getPacketData(), fullPacketData, offset);
						offset += p.getPacketData().length;
					}
					
					//
					CRC32 crc32 = new CRC32();
					crc32.update( packetData, 0, packetData.length);
					
					// remove cache
					_cache.remove( packetId );
					
					V5Packet fullPacket = new V5Packet(packetSender, packetType, packetReserved, packetId,  packetLength, 0, fullPacketData, crc32.getValue() );
					return fullPacket;
				}
			}
			return null;
		}
		return packet;
	}

}
