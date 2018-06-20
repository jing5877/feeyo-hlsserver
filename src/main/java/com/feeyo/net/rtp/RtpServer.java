package com.feeyo.net.rtp;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictor;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;

public class RtpServer {

	public static byte[] REPLY_BYTES = new byte[] { 0x11, 0x11 };

	private ConnectionlessBootstrap dataBootstrap;
	private ChannelFactory factory = new NioDatagramChannelFactory(Executors.newCachedThreadPool());

	private DatagramChannel dataChannel;

	public void startup(int port) {

		this.dataBootstrap = new ConnectionlessBootstrap(factory);
		dataBootstrap.setOption("receiveBufferSizePredictor", new FixedReceiveBufferSizePredictor(2048));
		dataBootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(2048));

		this.dataBootstrap.getPipeline().addLast("handler", new SimpleChannelUpstreamHandler() {
			@Override
			public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {

				ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

				if (buffer.readableBytes() < 12) {
					throw new IllegalArgumentException("A RTP packet must be at least 12 octets long");
				}

				byte b = buffer.readByte();
				byte version = (byte) (b & 0xc0);
				boolean padding = (b & 0x20) > 0; // mask 0010 0000
				boolean extension = (b & 0x10) > 0; // mask 0001 0000
				int contributingSourcesCount = b & 0x0f; // mask 0000 1111

				// Marker, Payload Type
				b = buffer.readByte();
				boolean marker = (b & 0x80) > 0; // mask 0000 0001
				int payloadType = (b & 0x7f); // mask 0111 1111

				int sequenceNumber = buffer.readUnsignedShort();
				long timestamp = buffer.readUnsignedInt();
				long ssrc = buffer.readUnsignedInt();

				// Read CCRC's
				if (contributingSourcesCount > 0) {
					for (int i = 0; i < contributingSourcesCount; i++) {
						long contributingSource = buffer.readUnsignedInt();
					}
				}

				// Read extension headers & data
				if (extension) {
					short extensionHeaderData = buffer.readShort();
					byte[] extensionData = new byte[buffer.readUnsignedShort() * 4];
					buffer.readBytes(extensionData);
				}

				if (!padding) {
					// No padding used, assume remaining data is the packet
					byte[] remainingBytes = new byte[buffer.readableBytes()];
					buffer.readBytes(remainingBytes);

					// remainingBytes == data
				} else {
					// Padding bit was set, so last byte contains the number of
					// padding octets that should be discarded.
					short lastByte = buffer.getUnsignedByte(buffer.readerIndex() + buffer.readableBytes() - 1);
					byte[] dataBytes = new byte[buffer.readableBytes() - lastByte];
					buffer.readBytes(dataBytes);

					// dataBytes == data

					// Discard rest of buffer.
					buffer.skipBytes(buffer.readableBytes());
				}

				// 应答
				ChannelBuffer replyBuffer = ChannelBuffers.copiedBuffer(REPLY_BYTES);
				e.getChannel().write(replyBuffer, e.getRemoteAddress());

			}
		});
		this.dataChannel = (DatagramChannel) this.dataBootstrap.bind(new InetSocketAddress(port));

	}

	public void close() {

		if (this.dataChannel != null)
			this.dataChannel.close();
		
		if (this.dataBootstrap != null)
			this.dataBootstrap.releaseExternalResources();

	}

}
