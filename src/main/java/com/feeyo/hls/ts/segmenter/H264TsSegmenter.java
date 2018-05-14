package com.feeyo.hls.ts.segmenter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import com.feeyo.mpeg2ts.TsWriter;
import com.feeyo.mpeg2ts.TsWriter.FrameData;
import com.feeyo.net.udp.packet.ByteUtil;
import com.feeyo.util.ts.codec.TsEncoder;

/**
 * 
 * @author xuwenfeng@variflight.com
 *
 */
public class H264TsSegmenter extends AbstractTsSegmenter{
	
	//NAL类型
	private static final int H264NT_SLICE  = 1;
	private static final int H264NT_SLICE_IDR = 5;
	private static final int H264NT_SPS = 7;	//SPS类型值
	private static final int H264NT_PPS = 8;	//PPS类型值
	private static final int H264NT_UNUSED_TYPE = 0; //未使用的类型
	
	//帧类型  
	private static final int FRAME_I    = 15;
	private static final int FRAME_P    = 16;  
	private static final int FRAME_B    = 17;
	private static final int UNSUPPORT  = -1;

	//由于视频数据可能大于65535，所以TsEncode时设置pes_packet_length = 0x0000(仅视频)
	private static final int TWO_FRAME_MAX_SIZE = 65535 * 2;
	
	//如果NALU对应的Slice为一帧的开始，则用4字节表示，即0x00000001；否则用3字节表示，0x000001
	private static final byte[] NAL_DELIMITER = {0x00, 0x00, 0x01}; 
	
	private boolean isFirstPes = true;
	private boolean waitingIDRFrame = true;				//等待关键帧
	private int currentNalType = 0;						//当前编码的帧类型（只记录I/B/P帧）
	private int numInGop = 0; 							//当前帧组中的第numInGop帧
	
	private byte[][] tsSecs;                			// 一个 tsSegment 包含几个 tsSecs: {tsSegment} = {[tsSec] [tsSec] ... [tsSec]}
	private int tsSecsPtr = 0;							// tsSecs 指针
	private int tsSegmentLen = 0;						// 一个 tsSegment 的字节数
	
	private RingBuffer framesBuf;						//用于缓存视频流过来的原始数据，可能存在多个帧
	
	private ArrayDeque<AvcFrame> avcFrameCache = new ArrayDeque<AvcFrame>();
	private List<AvcFrame> cacheAvcFrames = new ArrayList<AvcFrame>();
	
	private TsWriter tsWriter;
	private TsEncoder tsEncoder;
	
	public H264TsSegmenter() {
		
		super();
		
		frameNum = (int) (TS_DURATION * this.fps -1);
		ptsIncPerFrame = (long) (1000 / this.fps) * 90;
		pts += ptsIncPerFrame;
		dts = pts - 200;
		tsSegTime = frameNum * ptsIncPerFrame / 1000F;	//默认值
		
		tsWriter = new TsWriter();
		tsEncoder = new TsEncoder();
		
		framesBuf = new RingBuffer(TWO_FRAME_MAX_SIZE);
		tsSecs = new byte[3000][];
		
		prepare4NextTs();
	}
	
	
	
	@Override
	public void initialize(float sampleRate, int sampleSizeInBits, int channels, int fps) {
		this.fps = fps;
		frameNum = (int) (TS_DURATION * this.fps -1);
		ptsIncPerFrame = (long) (1000 / this.fps) * 90;
		pts += ptsIncPerFrame;
		dts = pts - 200;
		tsSegTime = frameNum * ptsIncPerFrame / 1000F;	//默认值
	}

	public void prepare4NextTs() {

		numInGop = 0;
		tsSecsPtr = 0;
		tsSegmentLen = 0;
		
		tsWriter.reset();
		tsEncoder.close();
		
		avcFrameCache.clear();
		
		for (int i = 0; i < tsSecs.length; i++) {
			tsSecs[i] = null;
		}
	}

	@Override
	protected byte[] segment(byte rawDataType, byte[] rawData, byte[] reserved) {
		
		byte[] tsSegment = null;
		boolean isNalDelimiter4 = false;
		byte nextNalType = H264NT_UNUSED_TYPE;
		
		int seekPos =  framesBuf.size() >= NAL_DELIMITER.length ? framesBuf.size() - NAL_DELIMITER.length : 0;
		
		framesBuf.add(rawData);
		byte[] src = framesBuf.elements(seekPos, framesBuf.size() - seekPos);
		
		//NAL的分隔符位置
		List<Integer> delimiters = ByteUtil.kmp(src, NAL_DELIMITER);
		
		for(int i=0; i<delimiters.size(); i++) {
			
			//取下一帧的帧类型
			if(delimiters.get(i) + NAL_DELIMITER.length < src.length)
				nextNalType = src[delimiters.get(i) + NAL_DELIMITER.length];
			else 
				break;
			
			//取当前完整帧的结束位置
			int endPos = i==0 ? seekPos + delimiters.get(0) : delimiters.get(i) - delimiters.get(i-1) + (isNalDelimiter4 ? 1: 0);
			
			//判断分隔符是否为0x00000001
			isNalDelimiter4 = ( endPos != 0 && i== 0 && delimiters.get(i) != 0 && src[delimiters.get(i)-1] == 0x00 );
			endPos = isNalDelimiter4 ? endPos -1 : endPos;
			
			if(waitingIDRFrame) {
				//判断下一次是否为IDR帧
				if(isIDRFrame(nextNalType))
					waitingIDRFrame = false;
				else {
					//移除IDR帧之前不完整的帧数据
					framesBuf.remove(0, endPos);
					continue;
				}
			}
			
			if(currentNalType == H264NT_SLICE || currentNalType == H264NT_SLICE_IDR) {
				
				//取到完整帧数据（其中IDR帧与IDR帧之前的SPS+PPS为一个整体）
				byte[] avcBuf = framesBuf.remove(0, endPos);
				
				if(avcBuf != null && avcBuf.length > NAL_DELIMITER.length) {
					
					boolean isLastFrame = (nextNalType & 0x1F) == H264NT_SPS;
					
					int frameType = H264NalUtil.getPesFrameType(avcBuf);
					
					List<AvcFrame> encodeAvcFrames = getEncodeAvcFrames( new AvcFrame(avcBuf, frameType, -1, getDts()), isLastFrame);
					numInGop++;
					
					for(AvcFrame avcFrame : encodeAvcFrames) {
						
						byte[] tsBuf = tsWriter.writeH264(isFirstPes, avcFrame.payload, avcFrame.payload.length, avcFrame.pts, avcFrame.dts);
						//byte[] tsBuf = tsEncoder.encode(false, avcFrame.payload, avcFrame.payload.length, avcFrame.pts, avcFrame.dts, true, isFirstPes);
						isFirstPes = false;
						if(tsBuf!= null) {
							tsSegmentLen += tsBuf.length;
				            tsSecs[tsSecsPtr++] = tsBuf;
				           // tsWriter.reset();
				            tsEncoder.close();
						}
					}
					
					tsSegTime = numInGop / this.fps;
					if(isLastFrame && tsSegTime >= 10F) {
						waitingIDRFrame = true;
						isFirstPes = true;
						tsSegTime = numInGop / this.fps;
						tsSegment = new byte[tsSegmentLen];
		                int tsSegmentPtr = 0;
						for (int j = 0; j < tsSecs.length; j++) {
							if(tsSecs[j] != null) {
			                    System.arraycopy(tsSecs[j], 0, tsSegment, tsSegmentPtr, tsSecs[j].length);
			                    tsSegmentPtr += tsSecs[j].length;
							}
		                }
		                prepare4NextTs();
		                
					}
				}
				
			}			
			//更新currentNalType
			if(((nextNalType & 0x1F) == H264NT_SLICE_IDR) || ((nextNalType & 0x1F) == H264NT_SLICE)) {
				currentNalType = nextNalType & 0x1F;
			}
			
			if(waitingIDRFrame)
				currentNalType = 0;
			
		}
		
		return tsSegment;
	}
	
	//mixed itf
	public AvcResult process(byte[] rawData) {

		boolean isNalDelimiter4 = false;
		byte nextNalType = H264NT_UNUSED_TYPE;
		
		int seekPos =  framesBuf.size() >= NAL_DELIMITER.length ? framesBuf.size() - NAL_DELIMITER.length : 0;
		
		framesBuf.add(rawData);
		byte[] src = framesBuf.elements(seekPos, framesBuf.size() - seekPos);
		
		List<Integer> delimiters = ByteUtil.kmp(src, NAL_DELIMITER);
		List<AvcFrame> encodeAvcFrames = new ArrayList<AvcFrame>();
		List<AvcFrame> endAvcFrames = new ArrayList<AvcFrame>();
		encodeAvcFrames.addAll(cacheAvcFrames);
		cacheAvcFrames.clear();
		boolean isTailAvc = false;
		
		for(int i=0; i<delimiters.size(); i++) {
			if(delimiters.get(i) + NAL_DELIMITER.length < src.length)
				nextNalType = src[delimiters.get(i) + NAL_DELIMITER.length];
			else 
				break;
			
			int endPos = i==0 ? seekPos + delimiters.get(0) : delimiters.get(i) - delimiters.get(i-1) + (isNalDelimiter4 ? 1: 0);
			
			isNalDelimiter4 = ( endPos != 0 && i== 0 && delimiters.get(i) != 0 && src[delimiters.get(i)-1] == 0x00 );
			endPos = isNalDelimiter4 ? endPos -1 : endPos;
			
			if(waitingIDRFrame) {
				if(isIDRFrame(nextNalType))
					waitingIDRFrame = false;
				else {
					framesBuf.remove(0, endPos);
					continue;
				}
			}
			
			if(currentNalType == H264NT_SLICE || currentNalType == H264NT_SLICE_IDR) {
				
				byte[] avcBuf = framesBuf.remove(0, endPos);
				
				if(avcBuf != null && avcBuf.length > NAL_DELIMITER.length) {
					
					boolean isLastFrame = (nextNalType & 0x1F) == H264NT_SPS;
					int frameType = H264NalUtil.getPesFrameType(avcBuf);
					
					encodeAvcFrames.addAll(getEncodeAvcFrames( new AvcFrame(avcBuf, frameType, -1, getDts()), isLastFrame));
					if(!isTailAvc && isLastFrame) {
						endAvcFrames.addAll(encodeAvcFrames);
						encodeAvcFrames.clear();
						isTailAvc = true;
						waitingIDRFrame = true;
					}
					
				}
			}
			if(((nextNalType & 0x1F) == H264NT_SLICE_IDR) || ((nextNalType & 0x1F) == H264NT_SLICE)) {
				currentNalType = nextNalType & 0x1F;
			}
			
			if(waitingIDRFrame)
				currentNalType = 0;
		}
		return endAvcFrames.isEmpty() && encodeAvcFrames.isEmpty() ? null : new AvcResult(isTailAvc ? endAvcFrames : encodeAvcFrames, isTailAvc);
	}
	
	@Override
	public void close() {
		
		if (tsSecs != null) {
            tsSecs = null;
        }
	}
	
	private List<AvcFrame> getEncodeAvcFrames(AvcFrame avcFrame, boolean isLastFrame) {
		List<AvcFrame> avcFrames = new ArrayList<AvcFrame>();
		
		switch(avcFrame.frameType) {
		case FRAME_I:
		case FRAME_P:
		case UNSUPPORT:
			if(!avcFrameCache.isEmpty()) {
				AvcFrame avcFrame2 = avcFrameCache.pop();
				avcFrame2.pts = getPts();
				avcFrames.add(avcFrame2);
				while(!avcFrameCache.isEmpty())
					avcFrames.add(avcFrameCache.pop());
			}
			break;
			
		case FRAME_B:
			avcFrame.pts = getPts();
			break;
		}
		
		avcFrameCache.offer(avcFrame);
		
		if(isLastFrame) {
			
			AvcFrame avcFrame2 = avcFrameCache.pop();
			avcFrame2.pts = getPts();
			avcFrames.add(avcFrame2);
			while(!avcFrameCache.isEmpty())
				avcFrames.add(avcFrameCache.pop());
		}
		
		return avcFrames;
	}
	
	private long getPts() {
		return pts += ptsIncPerFrame;
	}
	
	private long getDts() {
		return dts += ptsIncPerFrame;
	}
	
	private boolean isIDRFrame(byte nalType) {
		return (nalType & 0x1F) == H264NT_SPS ||(nalType & 0x1F) == H264NT_PPS || (nalType & 0x1F) == H264NT_SLICE_IDR;
	}
	
	static class AvcResult{
		
		public List<FrameData> avcFrames = new ArrayList<FrameData>();
		public boolean isTailAvc;
		
		public AvcResult(List<AvcFrame> avcFrames, boolean isTailAvc) {
			for(AvcFrame frame : avcFrames) {
				FrameData frameData = new FrameData();
				frameData.buf = frame.payload;
				frameData.pts = frame.pts;
				frameData.dts = frame.dts;
				frameData.isAudio = false;
				this.avcFrames.add(frameData);
			}
			this.isTailAvc = isTailAvc;
		}
	}
	
	static class AvcFrame{
		
		public byte[] payload;
		public int frameType;
		public long pts = -1;
		public long dts = -1;
		
		public AvcFrame(byte[] payload, int frameType, long pts, long dts) {
			this.payload = payload;
			this.frameType = frameType;
			this.pts = pts;
			this.dts = dts;
		}
	}
	
    static class RingBuffer{
    	private static final int capaStepLen = 5000; //扩容步长 
    	private int capacity = 0;
    	private int headPtr = 0;
    	private int tailPtr = 0;
    	
    	private byte[] elementData;
    	
    	public RingBuffer(int capacity) {
    		this.capacity = capacity;
    		elementData = new byte[capacity];
    		for(int i = 0; i<capacity; i++) {
    			elementData[i] = (byte)0xFF;
    		}
    	}
    	
    	public int size() {
    		return (tailPtr - headPtr + capacity) % capacity;
    	}
    	
    	public boolean isEmpty() {  
            return size() == 0;  
        } 
    	
    	public boolean isFull() {
    		return (tailPtr + 1) % capacity == headPtr;  
    	}
    	
    	private void add(byte element) {
    		if(isFull())
    			expandCapacity();

    		elementData[tailPtr] = element;
    		tailPtr = (tailPtr + 1) % capacity;
    	}
    	
    	private byte remove() {
    		if(isEmpty())
    			throw new NoSuchElementException("The buffer is already empty");
    		byte element = elementData[headPtr];
    		headPtr = (headPtr + 1) % capacity;
    		return element;
    	}
    	
    	public void add(byte[] elements) {
    		for(byte element : elements) {
    			add(element);
    		}
    	}
    	
    	public byte[] remove(int len) {
    		if(len > size())
    			return null;
    		
    		byte[] elements = new byte[len];
    		for(int i =0; i< len; i++) {
    			byte element = remove();
    			elements[i] = element;
    		}
    		return elements;
    	}
    	
    	//[0-from)的数据丢弃，再移除len位数据并返回,  from从0计数
    	public byte[] remove(int from, int len) {
    		if(from > size())
    			return null;
    		
			headPtr = (headPtr + from) % capacity;
			return remove(len);
    	}
    	
    	public byte[] elements() {
    		byte[] elements = new byte[size()];
    		for(int i=0; i<size(); i++) {
    			elements[i] = elementData[(headPtr + i) % capacity];
    		}
    		return elements;
    	}
    	
    	//from从0计算
    	public byte[] elements(int from, int len) {
    		if(from + len > size())
    			return null;
    		byte[] elements = new byte[len];
    		for(int i=0; i<len; i++) {
    			elements[i] = elementData[(headPtr + from + i) % capacity];
    		}
    		return elements;
    	}
    	
    	//pos从0计算
    	public byte get(int pos) {
    		return elementData[(headPtr + pos) % capacity];
    	}
    	
    	private void expandCapacity() {
    		byte[] copy = Arrays.copyOf(elementData, capacity+capaStepLen);
    		if(tailPtr < headPtr) {
    			System.arraycopy(elementData, headPtr, copy, headPtr + capaStepLen, capacity - headPtr);
    			headPtr += capaStepLen;
    		}
    		capacity += capaStepLen;
    		elementData = copy;
    	}
    	
    }
    
    public static void main(String[] args){
        byte[] a = {0x00, 0x00, 0x01};
        byte[] b = {0x00, 0x00, 0x01, 0x41, 0x16, (byte) 0x90, (byte) 0xFE, (byte) 0xB1,
        			0x5F, 0x5F,0x00, 0x00, 0x00, 0x01, 0x41, (byte) 0xE0,
        			0x5F, 0x5F,0x00, 0x00, 0x00, 0x01, 0x41, (byte) 0xE0,
        			(byte) 0xD1, 0x0B, (byte) 0xFF, (byte) 0x99, (byte) 0xC5,
        			0x5F, 0x5F,0x00, 0x00, 0x00, 0x01, 0x41, (byte) 0xE0};
        List<Integer> res = ByteUtil.kmp(b, a);
        System.out.println("kmp: " +res);
        for(int i=0; i<res.size(); i++) {
        	for(int j =0; j<a.length;j++)
        		System.out.print(b[res.get(i)+j]);
        	System.out.println();
        }
        
        System.out.println();
        RingBuffer ringBuf = new RingBuffer(100);
        ringBuf.add(a);
        ringBuf.add(b);
        
        System.out.println("base data");
        
        for(byte element : a) {
        	System.out.print(element + " ");
        }
        for(byte element : b) {
        	System.out.print(element + " ");
        }
        
        System.out.println();
        System.out.println("elements()");
        byte[] datas = ringBuf.elements();
        for(byte element : datas) {
        	System.out.print(element + " ");
        }
        
        System.out.println();
        System.out.println("elements(2, 5)");
        datas = ringBuf.elements(2, 5);
        for(byte element : datas) {
        	System.out.print(element + " ");
        }
        
        System.out.println();
        System.out.println("get(2)");
        System.out.println(ringBuf.get(2));
        
        System.out.println("remove(3)");
        datas = ringBuf.remove(3);
        for(byte data : datas)
        	System.out.print(data);
        
        System.out.println();
        System.out.println("remove(1, 5)");
        datas = ringBuf.remove(1,5);
        for(byte data : datas)
        	System.out.print(data + " ");
        System.out.println();
    }

	
}
