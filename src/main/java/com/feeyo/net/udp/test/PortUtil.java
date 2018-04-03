package com.feeyo.net.udp.test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortUtil {
	
	private static Logger LOGGER = LoggerFactory.getLogger( PortUtil.class );

	protected static final int minUdpPort = 6790;
	protected static final int maxUdpPort = 49150;
	protected static final AtomicInteger PORT_START = new AtomicInteger(minUdpPort);
	
	private static Set<Integer> reservedPorts = Collections.synchronizedSet(new HashSet<Integer>());

	public static synchronized void reservePort(int port) throws Exception {
		if (reservedPorts.contains(port))
			throw new Exception("Port " + port + "is reserved");
		reservedPorts.add(port);
	}

	public static synchronized void removePort(int port) {
		reservedPorts.remove(port);
	}

	public static synchronized boolean isPortReserved(int port) {
		return reservedPorts.contains(port);
	}


	public static synchronized int getNextNotReservedPort(int start) throws NoPortAvailableException {
		int port = start;
		while (reservedPorts.contains(port)) {
			if (port > maxUdpPort) {
				// port not found
				throw new NoPortAvailableException();
			}
			port += 1;
		}
		return port;
	}

	public static synchronized int[] findAvailablePorts(int nPorts) throws NoPortAvailableException {
		final int start = PORT_START.get();

		int[] ports = findAvailablePorts(nPorts, start);

		int newStart = ports[ports.length - 1];
		if (newStart >= 65535) {
			newStart = minUdpPort;
		}
		PORT_START.set((newStart + 1) / 2 * 2);

		return ports;
	}

	public static synchronized int[] findAvailablePorts(int nPorts, int startFrom) throws NoPortAvailableException {
		int dataPort, controlPort, startingPort;

		startingPort = startFrom;

		while (true) {

			startingPort = getNextNotReservedPort(startingPort);
			dataPort = getNextPortAvailable(startingPort);

			if (isPortReserved(dataPort)) {
				startingPort += nPorts;
				continue;
			}

			if (nPorts == 1) {
				// There is only the data port
				int[] a = { dataPort };
				LOGGER.debug("DataPort: " + dataPort);
				try {
					reservePort(dataPort);
				} catch (Exception e) {
					continue;
				}
				return a;

			} else if (nPorts == 2) {
				// We have to find 2 consequents free UDP ports.
				// also: dataPort must be an even number
				if ((dataPort % 2) != 0) {
					continue;

				} else {
					controlPort = getNextPortAvailable(dataPort + 1);

					if (controlPort != (dataPort + 1)) {
						// port are not consequents
						continue;
					} else if (isPortReserved(controlPort)) {
						continue;

					} else {
						try {
							reservePort(dataPort);
							reservePort(controlPort);
						} catch (Exception e) {
							continue;
						}

						int[] a = { dataPort, controlPort };
						LOGGER.debug("DataPort: " + dataPort + " - ControlPort: " + controlPort);
						return a;
					}
				}
			}
		}
	}

	private static int getNextPortAvailable(int startPort) throws NoPortAvailableException {

		for (int port = startPort; port <= maxUdpPort; port++) {
			DatagramSocket s = null;
			try {
				s = new DatagramSocket(port);
				s.close();
				return port;

			} catch (IOException e) {
				// Ignore
			}
		}
		// No port is available
		throw new NoPortAvailableException();
	}

	public static class NoPortAvailableException extends IOException {
		static final long serialVersionUID = 0x33DD33DD55L;

		@Override
		public String getMessage() {
			return "No UDP port available";
		}
	}

}
