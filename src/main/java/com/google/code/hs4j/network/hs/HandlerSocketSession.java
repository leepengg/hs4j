/**
 *Copyright [2009-2010] [dennis zhuang(killme2008@gmail.com)]
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *             http://www.apache.org/licenses/LICENSE-2.0
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *either express or implied. See the License for the specific language governing permissions and limitations under the License
 */
package com.google.code.hs4j.network.hs;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.google.code.hs4j.Command;
import com.google.code.hs4j.CommandFactory;
import com.google.code.hs4j.network.core.WriteMessage;
import com.google.code.hs4j.network.nio.NioSessionConfig;
import com.google.code.hs4j.network.nio.impl.NioTCPSession;
import com.google.code.hs4j.network.util.LinkedTransferQueue;
import com.google.code.hs4j.network.util.SystemUtils;

/**
 * Connected session for a handlersocket server
 * 
 * @author dennis
 */
public class HandlerSocketSession extends NioTCPSession {

	/**
	 * Command which are already sent
	 */
	protected BlockingQueue<Command> commandAlreadySent;

	private final AtomicReference<Command> currentCommand = new AtomicReference<Command>();

	private SocketAddress remoteSocketAddress; // prevent channel is closed
	private int sendBufferSize;

	private volatile boolean allowReconnect;

	private final CommandFactory commandFactory;

	public HandlerSocketSession(NioSessionConfig sessionConfig,
			int readRecvBufferSize, int readThreadCount,
			CommandFactory commandFactory) {
		super(sessionConfig, readRecvBufferSize);
		if (this.selectableChannel != null) {
			this.remoteSocketAddress = ((SocketChannel) this.selectableChannel)
					.socket().getRemoteSocketAddress();
			this.allowReconnect = true;
			try {
				this.sendBufferSize = ((SocketChannel) this.selectableChannel)
						.socket().getSendBufferSize();
			} catch (SocketException e) {
				this.sendBufferSize = 8 * 1024;
			}
		}
		this.commandAlreadySent = new LinkedTransferQueue<Command>();
		this.commandFactory = commandFactory;
	}

	@Override
	public String toString() {
		return SystemUtils.getRawAddress(this.getRemoteSocketAddress()) + ":"
				+ this.getRemoteSocketAddress().getPort();
	}

	public void destroy() {
		Command command = this.currentCommand.get();
		if (command != null) {
			command.setExceptionMessage("Connection has been closed");
			command.countDown();
		}
		while ((command = this.commandAlreadySent.poll()) != null) {
			command.setExceptionMessage("Connection has been closed");
			command.countDown();
		}

	}

	@Override
	public InetSocketAddress getRemoteSocketAddress() {
		InetSocketAddress result = super.getRemoteSocketAddress();
		if (result == null && this.remoteSocketAddress != null) {
			result = (InetSocketAddress) this.remoteSocketAddress;
		}
		return result;
	}

	@Override
	protected final WriteMessage wrapMessage(Object msg,
			Future<Boolean> writeFuture) {
		((Command) msg).encode();
		((Command) msg).setWriteFuture(writeFuture);
		if (log.isDebugEnabled()) {
			log.debug("After encoding" + ((Command) msg).toString());
		}
		return super.wrapMessage(msg, writeFuture);
	}

	/**
	 * get current command from queue
	 * 
	 * @return
	 */
	private final Command takeExecutingCommand() {
		try {
			return this.commandAlreadySent.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	/**
	 * is allow auto recconect if closed?
	 * 
	 * @return
	 */
	public boolean isAllowReconnect() {
		return this.allowReconnect;
	}

	public void setAllowReconnect(boolean reconnected) {
		this.allowReconnect = reconnected;
	}

	public final void addCommand(Command command) {
		this.commandAlreadySent.add(command);
	}

	public final void setCurrentCommand(Command cmd) {
		this.currentCommand.set(cmd);
	}

	public final Command getCurrentCommand() {
		return this.currentCommand.get();
	}

	public final void takeCurrentCommand() {
		this.setCurrentCommand(this.takeExecutingCommand());
	}

	// TODO
	public void quit() {
		// this.write(this.commandFactory.createQuitCommand());
	}
}