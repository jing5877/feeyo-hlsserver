package com.feeyo.audio.codec.faac;

/**
 * TODO brief desc
 *
 * @author Tr!bf wangyamin@variflight.com
 */
public enum AacError {
    UNKNOWN_ERROR(-1000, "unknow error"),
    JNI_ENCODER_BROKEN(-1, "[JNI] the created faac encoder is broken"),
    JNI_INVALIDE_ADDRESS(-2, "[JNI] invalide faac address"),
    JNI_ENCODE_ERROR(-3, "[JNI] error while encoding"),
    JNI_GET_DATA_FAILED(-10, "[JNI] get pcm data failed"),
    INPUT_DATA_TOO_LARGE(-20, "input data too large");

    private final int code;
    private final String description;

    AacError(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getDesc() {
        return description;
    }

    public int getCode() {
        return code;
    }

    public static AacError getErrorByCode(int code) {
        for (AacError error : AacError.values()) {
            if (error.getCode() == code) {
                return error;
            }
        }
        return UNKNOWN_ERROR;
    }

    @Override
    public String toString() {
        return code + ": " + description;
    }
}
