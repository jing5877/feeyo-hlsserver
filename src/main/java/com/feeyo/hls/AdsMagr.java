package com.feeyo.hls;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.ts.TsSegment;
import com.feeyo.hls.ts.segmenter.AacTranscodingTsSegmenter;

/**
 * Advertisement watch dog
 *
 * @author Tr!bf wangyamin@variflight.com
 *
 * the advertisement must in wav format (S16LE) without metadata
 * sample rate : 8000, channel : 1
 * the file name is advertisement\d*.wav and sorted by lexicographical order
 */
public class AdsMagr implements Runnable {
	
	private static Logger LOGGER = LoggerFactory.getLogger( AdsMagr.class );
	
	
	private static volatile boolean isHasAds = false;
	
    private static final int WAV_HEADER_LENGTH = 44;
    private static final int DELAY = 60 * 1000;                                      // in ms, every minute
    private static final String ADS_REGEX = "^advertisement\\d*\\.wav$";             // only support wav advertisement for adding zero padding between ads
    private File adsDirectory;

    private Map<String, byte[]> adsRawData = new HashMap<String, byte[]>();          // the advertisement loaded in wav
    private Map<String, Long> adsMtime = new HashMap<String, Long>();
    private int adsRawDataLen = 0;
    private byte[] adsRawDataBuf;

    private List<TsSegment> adsTsSegments = new ArrayList<>();

    private boolean interrupted = false;
    private AacTranscodingTsSegmenter hlsTsSegmenter;

    public AdsMagr (String adsDirPath) {
        adsDirectory = new File(adsDirPath);
        hlsTsSegmenter = new AacTranscodingTsSegmenter();
    }

    public void checkAndConfigure () {
        boolean needRegenerate = false;
        Set<String> adsNames = new HashSet<>();
        if (adsDirectory.exists() && adsDirectory.isDirectory()) {
            File[] files = adsDirectory.listFiles();
            for (File f : files) {
                if (f.isFile() && f.getName().matches(ADS_REGEX)) {
                    adsNames.add(f.getName());

                    if (adsMtime.get(f.getName()) == null || adsMtime.get(f.getName()) != f.lastModified()) {

                        adsMtime.put(f.getName(),f.lastModified());

                        if (adsRawData.get(f.getName()) != null) {
                            byte[] oldAds = adsRawData.remove(f.getName());
                            adsRawDataLen -= oldAds.length;
                        }

                        InputStream in = null;
                        try {
                            in = new FileInputStream(f);
                            byte[] adRawData = new byte[(int)f.length()-WAV_HEADER_LENGTH];
                            in.skip(WAV_HEADER_LENGTH);
                            in.read(adRawData,0,adRawData.length);

                            adsRawData.put(f.getName(),adRawData);
                            adsRawDataLen += adRawData.length;
                        } catch (Exception e) {
                        	LOGGER.error("", e);
                        } finally {
                            try {
                                if (in != null)
                                    in.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                        needRegenerate = true;
                    }
                }
            }

            // remove the no longer exist ads
            for (String ad : adsMtime.keySet()) {
                if (!adsNames.contains(ad)) {
                    adsRawDataLen -= adsRawData.get(ad).length;
                    adsMtime.remove(ad);
                    adsRawData.remove(ad);
                    needRegenerate = true;
                }
            }

            if (needRegenerate) {
                Set<String> adNames = adsRawData.keySet();
                List<String> adNamesSorted = new ArrayList<String>(adNames);
                Collections.sort(adNamesSorted, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return o1.compareToIgnoreCase(o2);
                    }
                });

                // make the advertisements raw buffer 2048-byte aligned
                int adsRawDataBufLen = adsRawDataLen + ((adsRawDataLen & 0x7FF)==0 ? 0 : 2048);
                adsRawDataBuf = new byte[adsRawDataBufLen];
                int adsRawBufPtr = 0;

                for (String adName : adNamesSorted) {
                    byte[] data = adsRawData.get(adName);
                    System.arraycopy(data,0, adsRawDataBuf, adsRawBufPtr, data.length);
                    adsRawBufPtr += data.length;
                }

                hlsTsSegmenter.setPts(hlsTsSegmenter.getPtsIncPerFrame() * 3);	//3 ： TS_PES_AU_NUM
//                byte[][] adsTs = hlsTsSegmenter.segmentOneOff(adsRawDataBuf);
//                if ( adsTs != null ) {
//	                List<TsSegment> tsSegments = new ArrayList<>();
//	                for (int i=0; i<adsTs.length; i++) {
//	                    tsSegments.add(new TsSegment((i+1)+".ts",adsTs[i],hlsTsSegmenter.getTsSegTime(),true));
//	                }
//	                adsTsSegments = tsSegments;
//                }
            }
        }
    }

    public List<TsSegment> getAdsTsSegments() {
        return adsTsSegments;
    }

    @Override
    public void run() {
        while (!interrupted) {
            checkAndConfigure();
            try {
                Thread.sleep(DELAY);
            } catch (InterruptedException e) {

            }
        }
    }

    public void close() {
        interrupted = true;
        hlsTsSegmenter.close();
    }
    

	public static boolean isHasAds() {
		return isHasAds;
	}

	public static void setHasAds(boolean isHasAds) {
		AdsMagr.isHasAds = isHasAds;
	}
    
    
}
