package com.feeyo.audio.codec.faac;

/**
 * Faac encode exception
 *
 * @author Tr!bf wangyamin@variflight.com
 */
public class AacEncodeException extends Exception {

	private static final long serialVersionUID = 3696881744605234098L;
	
	private AacError error;

    public AacEncodeException(int errorCode) {
        super("faacEncEncode error: " + errorCode);
        error =  AacError.getErrorByCode(errorCode);
    }

    public AacEncodeException(AacError error) {
        this.error = error;
    }

    public AacError getError() {
        return error;
    }


}
