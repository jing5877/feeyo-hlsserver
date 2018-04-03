package com.feeyo.net.udp.test.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractReportPacket extends ControlPacket {

    protected long senderSsrc;
    protected List<ReceptionReport> receptionReports;

    protected AbstractReportPacket(Type type) {
        super(type);
    }

    public boolean addReceptionReportBlock(ReceptionReport block) {
        if (this.receptionReports == null) {
            this.receptionReports = new ArrayList<ReceptionReport>();
            return this.receptionReports.add(block);
        }

        // 5 bits is the limit
        return (this.receptionReports.size() < 31) && this.receptionReports.add(block);
    }

    public byte getReceptionReportCount() {
        if (this.receptionReports == null) {
            return 0;
        }

        return (byte) this.receptionReports.size();
    }


    public long getSenderSsrc() {
        return senderSsrc;
    }

    public void setSenderSsrc(long senderSsrc) {
        if ((senderSsrc < 0) || (senderSsrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }
        this.senderSsrc = senderSsrc;
    }

    public List<ReceptionReport> getReceptionReports() {
        if (this.receptionReports == null) {
            return null;
        }
        return Collections.unmodifiableList(this.receptionReports);
    }

    public void setReceptionReports(List<ReceptionReport> receptionReports) {
        if (receptionReports.size() >= 31) {
            throw new IllegalArgumentException("At most 31 report blocks can be sent in a *ReportPacket");
        }
        this.receptionReports = receptionReports;
    }
}
