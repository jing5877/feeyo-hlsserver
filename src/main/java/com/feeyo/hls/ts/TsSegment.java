package com.feeyo.hls.ts;

/**
 * hls ts segment
 *
 * @author Tr!bf wangyamin@variflight.com
 * @author zhuam
 */
public class TsSegment {
	
    private final long ctime;
    private String name;
    private byte[] data;
    private float duration;
    private boolean isAds;
    private boolean isDiscontinue;
    
    private long lasttime;

    public TsSegment(String name, byte[] data, float duration, boolean isAds) {
    	
    	long now = System.currentTimeMillis();
        this.ctime = now;
        this.name = name;
        this.data = data;
        this.duration = duration;
        this.isAds = isAds;
        this.lasttime = now;
    }

    public long getCtime() {
        return ctime;
    }

    public String getName() {
        return name;
    }

    public byte[] getData() {
        return data;
    }

    public float getDuration() {
        return duration;
    }

    public boolean isAds() {
        return isAds;
    }

    public boolean isDiscontinue() {
        return isDiscontinue;
    }

    public void setDiscontinue(boolean discontinue) {
        isDiscontinue = discontinue;
    }

	public long getLasttime() {
		return lasttime;
	}

	public void setLasttime(long lasttime) {
		this.lasttime = lasttime;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer(80);
		sb.append("name=").append( name ).append(", ");
		sb.append("ctime=").append( ctime ).append(", ");
		sb.append("duration=").append( duration ).append(", ");
		sb.append("isAds=").append( isAds );
		
		return sb.toString();
	}
    
}
