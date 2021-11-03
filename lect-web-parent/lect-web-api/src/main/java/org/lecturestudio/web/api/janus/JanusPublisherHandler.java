/*
 * Copyright (C) 2021 TU Darmstadt, Department of Computer Science,
 * Embedded Systems and Applications Group.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.lecturestudio.web.api.janus;

import static java.util.Objects.nonNull;

import org.lecturestudio.core.ExecutableException;
import org.lecturestudio.core.beans.ChangeListener;
import org.lecturestudio.web.api.janus.message.JanusMessage;
import org.lecturestudio.web.api.janus.message.JanusPluginMessage;
import org.lecturestudio.web.api.janus.state.AttachPluginState;
import org.lecturestudio.web.api.janus.state.CreateRoomState;
import org.lecturestudio.web.api.stream.StreamEventRecorder;
import org.lecturestudio.web.api.stream.action.StreamAction;

public class JanusPublisherHandler extends JanusStateHandler {

	private final StreamEventRecorder eventRecorder;

	private ChangeListener<Boolean> enableMicListener;

	private ChangeListener<Boolean> enableCamListener;


	public JanusPublisherHandler(JanusPeerConnectionFactory factory,
			JanusMessageTransmitter transmitter,
			StreamEventRecorder eventRecorder) {
		super(factory, transmitter);

		this.eventRecorder = eventRecorder;
	}

	@Override
	public <T extends JanusMessage> void handleMessage(T message) throws Exception {
		if (message instanceof JanusPluginMessage) {
			JanusPluginMessage pluginMessage = (JanusPluginMessage) message;

			// Accept only messages that are addressed to this handler.
			if (!pluginMessage.getHandleId().equals(getPluginId())) {
				return;
			}
		}

		super.handleMessage(message);
	}

	@Override
	public JanusPeerConnection createPeerConnection() {
		JanusPeerConnection peerConnection = super.createPeerConnection();
		peerConnection.setOnException(this::setError);
		peerConnection.setOnIceConnectionState(state -> {
			switch (state) {
				case CONNECTED:
					setConnected();
					break;

				case DISCONNECTED:
				case CLOSED:
					setDisconnected();
					break;
			}
		});

		getWebRtcConfig().getAudioConfiguration().sendAudioProperty()
				.addListener(enableMicListener);
		getWebRtcConfig().getVideoConfiguration().sendVideoProperty()
				.addListener(enableCamListener);

		return peerConnection;
	}

	@Override
	protected void initInternal() throws ExecutableException {
		enableMicListener = (observable, oldValue, newValue) -> {
			peerConnection.enableMicrophone(newValue);
		};

		enableCamListener = (observable, oldValue, newValue) -> {
			peerConnection.enableCamera(newValue);
		};
	}

	@Override
	protected void startInternal() throws ExecutableException {
		eventRecorder.addRecordedActionConsumer(this::sendStreamAction);

		setState(new AttachPluginState(new CreateRoomState()));
	}

	@Override
	protected void stopInternal() throws ExecutableException {
		eventRecorder.removeRecordedActionConsumer(this::sendStreamAction);

		getWebRtcConfig().getAudioConfiguration().sendAudioProperty()
				.removeListener(enableMicListener);
		getWebRtcConfig().getVideoConfiguration().sendVideoProperty()
				.removeListener(enableCamListener);

		if (nonNull(peerConnection)) {
			peerConnection.close();
			peerConnection = null;
		}
	}

	@Override
	protected void destroyInternal() throws ExecutableException {

	}

	public void sendStreamAction(StreamAction action) {
		JanusPeerConnection peerConnection = getPeerConnection();

		if (nonNull(peerConnection)) {
			try {
				peerConnection.sendData(action.toByteArray());
			}
			catch (Exception e) {
				logException(e, "Send event via data channel failed");
			}
		}
	}
}
