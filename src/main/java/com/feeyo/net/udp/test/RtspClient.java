package com.feeyo.net.udp.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.feeyo.util.InetAddressUtil;

public class RtspClient extends Thread implements IEvent {

    private static final String VERSION = " RTSP/1.0";
    private static final String RTSP_OK = "RTSP/1.0 200 OK";
    
    private String remoteHost;	//远程地址
    private int rtspPort;	//远程地址

    private SocketChannel socketChannel;
    private final ByteBuffer sendBuf;				//发送缓冲区 
    private final ByteBuffer receiveBuf;			//接收缓冲区

    private static final int BUFFER_SIZE = 8192;

    private Selector selector;
    private Status sysStatus;
    private String sessionid;

    private AtomicBoolean shutdown;					//线程是否结束的标志
    
    private int seq=1;
    private boolean isSended;
    
    private String device_md5;
    
    private enum Status {
        init, options, describe, setup, play, pause, teardown
    }

    public RtspClient(String url) {
    	
    	Pattern pattern = Pattern.compile("^rtsp://([^:/]+)(:([0-9]+))?/(([A-Za-z0-9]+))");
		Matcher m = pattern.matcher(url);
		if ( !m.find() ) {			
			System.out.println("Illegal RTSP address[" + url + "]");			
			throw new IllegalArgumentException("Illegal RTSP address[" + url + "]");
		}
		this.remoteHost = m.group(1);
		this.rtspPort = Integer.parseInt(m.group(3));
		this.device_md5 = m.group(4);

        // 初始化缓冲区
        sendBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        receiveBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        if (selector == null) {
            // 创建新的Selector
            try {
                selector = Selector.open();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

        startup();
        sysStatus = Status.init;
        shutdown=new AtomicBoolean(false);
        isSended=false;
    }

    public void startup() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.socket().setSoTimeout(30000);
            socketChannel.configureBlocking(false);
            socketChannel.socket().bind(new InetSocketAddress(InetAddressUtil.getLocalHostIp(), 0));
            InetSocketAddress addr = new InetSocketAddress(remoteHost, rtspPort);
            if (socketChannel.connect(addr)) {
                System.out.println("开始建立连接:" + addr);
            }
            socketChannel.register(selector, SelectionKey.OP_CONNECT
                    | SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
            System.out.println("端口打开成功");

        } catch (final IOException e1) {
            e1.printStackTrace();
        }
    }

    public void send(byte[] out) {
        if (out == null || out.length < 1) {
            return;
        }
        synchronized (sendBuf) {
            sendBuf.clear();
            sendBuf.put(out);
            sendBuf.flip();
        }

        // 发送出去
        try {
            write();
            isSended=true;
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void write() throws IOException {
        if (isConnected()) {
            try {
                socketChannel.write(sendBuf);
            } catch (final IOException e) {
            }
        } else {
            System.out.println("通道为空或者没有连接上");
        }
    }

    public byte[] recieve() {
        if (isConnected()) {
            try {
                int len = 0;
                int readBytes = 0;

                synchronized (receiveBuf) {
                    receiveBuf.clear();
                    try {
                        while ((len = socketChannel.read(receiveBuf)) > 0) {
                            readBytes += len;
                        }
                    } finally {
                        receiveBuf.flip();
                    }
                    if (readBytes > 0) {
                        final byte[] tmp = new byte[readBytes];
                        receiveBuf.get(tmp);
                        return tmp;
                    } else {
                        System.out.println("接收到数据为空,重新启动连接");
                        return null;
                    }
                }
            } catch (final IOException e) {
                System.out.println("接收消息错误:");
            }
        } else {
            System.out.println("端口没有连接");
        }
        return null;
    }

    public boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    private void select() {
        int n = 0;
        try {
            if (selector == null) {
                return;
            }
            n = selector.select(1000);

        } catch (final Exception e) {
            e.printStackTrace();
        }

        // 如果select返回大于0，处理事件
        if (n > 0) {
            for (final Iterator<SelectionKey> i = selector.selectedKeys()
                    .iterator(); i.hasNext();) {
                // 得到下一个Key
                final SelectionKey sk = i.next();
                i.remove();
                // 检查其是否还有效
                if (!sk.isValid()) {
                    continue;
                }

                // 处理事件
                final IEvent handler = (IEvent) sk.attachment();
                try {
                    if (sk.isConnectable()) {
                        handler.connect(sk);
                    } else if (sk.isReadable()) {
                        handler.read(sk);
                    } else {
                        // System.err.println("Ooops");
                    }
                } catch (final Exception e) {
                    handler.error(e);
                    sk.cancel();
                }
            }
        }
    }

    public void shutdown() {
        if (isConnected()) {
            try {
                socketChannel.close();
                System.out.println("端口关闭成功");
            } catch (final IOException e) {
                System.out.println("端口关闭错误:");
            } finally {
                socketChannel = null;
            }
        } else {
            System.out.println("通道为空或者没有连接");
        }
    }

    @Override
    public void run() {
        // 启动主循环流程
        while (!shutdown.get()) {
            try {
                if (isConnected()&&(!isSended)) {
                    switch (sysStatus) {
                    case init:
                        doOption();
                        break;
                    case options:
                        doDescribe();
                        break;
                    case describe:
                        doSetup();
                        break;
                    case setup:
                        if(sessionid==null&&sessionid.length()>0){
                            System.out.println("setup还没有正常返回");
                        }else{
                            doPlay();
                        }
                        break;
//                    case play:
//                        doPause();
//                        break;
//                        
//                    case pause:
//                        doTeardown();
//                        break;
                    default:
                        break;
                    }
                }
                // do select
                select();
                try {
                    Thread.sleep(1000);
                } catch (final Exception e) {
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        
        shutdown();
    }

    @Override
    public void connect(SelectionKey key) throws IOException {
        if (isConnected()) {
            return;
        }
        // 完成SocketChannel的连接
        socketChannel.finishConnect();
        while (!socketChannel.isConnected()) {
            try {
                Thread.sleep(300);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            socketChannel.finishConnect();
        }

    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
    }

    @Override
    public void read(SelectionKey key) throws IOException {
        // 接收消息
        final byte[] msg = recieve();
        if (msg != null) {
            handle(msg);
        } else {
            key.cancel();
        }
    }

    private void handle(byte[] msg) {
        String content = new String(msg);
        System.out.println("ret content："+content);
        if (content.startsWith(RTSP_OK)) {
            switch (sysStatus) {
            case init:
                sysStatus = Status.options;
                System.out.println("option ok");
                break;
            case options:
                sysStatus = Status.describe;
                System.out.println("describe ok");
                break;
            case describe:
                sessionid = content.substring(content.indexOf("Session: ") + 9, content
                        .indexOf("Transport:"));
                if(sessionid!=null&&sessionid.length()>0){
                    sysStatus = Status.setup;
                    System.out.println("setup ok");
                }
                break;
            case setup:
                sysStatus = Status.play;
                System.out.println("play ok");
                break;
            case play:
                sysStatus = Status.pause;
                System.out.println("pause ok");
                break;
            case pause:
                sysStatus = Status.teardown;
                System.out.println("teardown ok");
                shutdown.set(true);
                break;
            case teardown:
                sysStatus = Status.init;
                System.out.println("exit start");
                break;
            default:
                break;
            }
            isSended=false;
        } else {
            System.out.println("返回错误：" + content);
        }

    }

    private void doTeardown() {
        StringBuilder sb = new StringBuilder();
        sb.append("TEARDOWN ");
        sb.append(remoteHost).append(":").append(rtspPort);
        sb.append("/");
        sb.append(VERSION).append(System.lineSeparator());
        sb.append("Cseq: ");
        sb.append(seq++);
        sb.append(System.lineSeparator());
        sb.append("User-Agent: RealMedia Player HelixDNAClient/10.0.0.11279 (win32)").append(System.lineSeparator());
        sb.append("Session: ");
        sb.append(sessionid);
        sb.append(System.lineSeparator());
        send(sb.toString().getBytes());
        System.out.println(sb.toString());
    }

    private void doPlay() {
        StringBuilder sb = new StringBuilder();
        sb.append("PLAY ");
        sb.append(remoteHost).append(":").append(rtspPort);
        sb.append(VERSION).append(System.lineSeparator());
        sb.append("Session: ");
        sb.append(sessionid);
        sb.append("Cseq: ");
        sb.append(seq++);
        sb.append(System.lineSeparator());
        sb.append("Range: ");
        sb.append("nap=0.000-");
        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());
        System.out.println(sb.toString());
        send(sb.toString().getBytes());

    }

    private void doSetup() {
        StringBuilder sb = new StringBuilder();
        sb.append("SETUP ");
        sb.append(remoteHost).append(":").append(rtspPort);
        sb.append("/");
        sb.append(device_md5);
        sb.append("/live");
        sb.append(VERSION).append(System.lineSeparator());
        sb.append("Cseq: ");
        sb.append(seq++);
        sb.append(System.lineSeparator());
        sb.append("Transport: RTP/UDP;UNICAST;client_port=8360-8361;mode=play").append(System.lineSeparator());
        sb.append(System.lineSeparator());
        System.out.println(sb.toString());
        send(sb.toString().getBytes());
    }

    private void doOption() {
        StringBuilder sb = new StringBuilder();
        sb.append("OPTIONS ");
        sb.append(remoteHost).append(":").append(rtspPort);
        sb.append(VERSION).append(System.lineSeparator());
        sb.append("Cseq: ");
        sb.append(seq++);
        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());
        System.out.println(sb.toString());
        send(sb.toString().getBytes());
    }

    private void doDescribe() {
        StringBuilder sb = new StringBuilder();
        sb.append("DESCRIBE ");
        sb.append(remoteHost).append(":").append(rtspPort);
        sb.append(VERSION).append(System.lineSeparator());
        sb.append("Cseq: ");
        sb.append(seq++);
        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());
        System.out.println(sb.toString());
        send(sb.toString().getBytes());
    }
    
    private void doPause() {
        StringBuilder sb = new StringBuilder();
        sb.append("PAUSE ");
        sb.append(remoteHost).append(":").append(rtspPort);
        sb.append("/");
        sb.append(VERSION).append(System.lineSeparator());
        sb.append("Cseq: ");
        sb.append(seq++);
        sb.append(System.lineSeparator());
        sb.append("Session: ");
        sb.append(sessionid);
        sb.append(System.lineSeparator());
        send(sb.toString().getBytes());
        System.out.println(sb.toString());
    }

	public void close() {
		
		shutdown.set(true);
        isSended=false;
        
		if(socketChannel != null) {
			try {
				socketChannel.close();
			} catch (IOException e) {
			}
		}
		
		if(selector != null) {
			try {
				selector.close();
			} catch (IOException e) {
			}
		}
		
	}
    
}

