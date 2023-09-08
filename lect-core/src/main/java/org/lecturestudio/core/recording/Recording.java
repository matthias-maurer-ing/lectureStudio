/*
 * Copyright (C) 2020 TU Darmstadt, Department of Computer Science,
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

package org.lecturestudio.core.recording;

import static java.util.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.lecturestudio.core.model.Interval;
import org.lecturestudio.core.recording.edit.RecordingEditManager;

public class Recording {

	/**
	 * @param interval The interval of the recording, including its start and endpoint in ms.
	 * @param fileName The name of the file including its extension.
	 */
	public record ToolDemoRecording(Interval<Long> interval, String fileName) {
		public ToolDemoRecording clone() {
			return new ToolDemoRecording(new Interval<Long>(this.interval.getStart(), this.interval.getEnd()), this.fileName);
		}
	}

	public static final int FORMAT_VERSION = 3;

	public enum Content {
		ALL, HEADER, AUDIO, DOCUMENT, EVENTS_ADDED, EVENTS_CHANGED, EVENTS_REMOVED
	}

	private final List<RecordingChangeListener> listeners = new ArrayList<>();

	private final RecordingEditManager editManager = new RecordingEditManager();

	/**
	 * The source file.
	 */
	private File sourceFile;

	private RecordingHeader header;

	private RecordedAudio audio;

	private RecordedDocument document;

	private RecordedEvents events;

	private String cameraRecordingFileName;

	private List<ToolDemoRecording> toolDemoRecordings = new ArrayList<>();

	public Recording() {
		header = new RecordingHeader();
	}

	/**
	 * Copies all mutable object variables from the supplied recording.
	 *
	 * @param recording The recording to copy the variables from.
	 * @throws IOException
	 */
	public Recording(Recording recording) throws IOException {
		this.setRecordedDocument(new RecordedDocument(recording.getRecordedDocument().toByteArray()));
		this.setRecordedAudio(new RecordedAudio(recording.getRecordedAudio().getAudioStream().clone()));
		this.setRecordedEvents(new RecordedEvents(recording.getRecordedEvents().toByteArray()));
		this.setRecordingHeader(recording.getRecordingHeader().clone());
	}

	public File getSourceFile() {
		return sourceFile;
	}

	public void setSourceFile(File file) {
		this.sourceFile = file;
	}

	public RecordingEditManager getEditManager() {
		return editManager;
	}

	public RecordingHeader getRecordingHeader() {
		return header;
	}

	public void setRecordingHeader(RecordingHeader header) {
		this.header = header;

		fireChangeEvent(Content.HEADER);
	}

	public RecordedAudio getRecordedAudio() {
		return audio;
	}

	public void setRecordedAudio(RecordedAudio audio) {
		this.audio = audio;

		fireChangeEvent(Content.AUDIO);
	}

	public RecordedEvents getRecordedEvents() {
		return events;
	}

	public void setRecordedEvents(RecordedEvents actions) {
		this.events = actions;

		fireChangeEvent(Content.EVENTS_REMOVED);
	}

	public RecordedDocument getRecordedDocument() {
		return document;
	}

	public void setRecordedDocument(RecordedDocument document) {
		this.document = document;

		fireChangeEvent(Content.DOCUMENT);
	}

	public String getCameraRecordingFileNameData() {
		return cameraRecordingFileName;
	}

	public byte[] getCameraRecordingFileNameDataAsByteStream() {
		return cameraRecordingFileName.getBytes(StandardCharsets.UTF_8);
	}

	public void setCameraRecordingFileNameData(String cameraRecordingFileName) {
		this.cameraRecordingFileName = cameraRecordingFileName;
	}

	public List<ToolDemoRecording> getToolDemoRecordingsData() {
		return this.toolDemoRecordings;
	}

	public void setToolDemoRecordingsData(List<ToolDemoRecording> toolDemoRecordings) {
		this.toolDemoRecordings = toolDemoRecordings;
	}

	public void addToolDemoRecordingsData(ToolDemoRecording toolDemoRecordings) {
		this.toolDemoRecordings.add(toolDemoRecordings);
	}

	public byte[] getToolDemoRecordingsDataAsByteStream() {
		ByteBuffer buffer = ByteBuffer.allocate(getToolDemoRecordingsByteLength());

		for (ToolDemoRecording toolDemoRecording : toolDemoRecordings) {
			byte[] nameBuffer = toolDemoRecording.fileName.getBytes(StandardCharsets.UTF_8);

			buffer.putLong(toolDemoRecording.interval.getStart());
			buffer.putLong(toolDemoRecording.interval.getEnd());
			buffer.putInt(nameBuffer.length);
			buffer.put(nameBuffer);
		}
		return buffer.array();
	}

	public void close() {
		if (nonNull(getRecordedDocument().getDocument())) {
			getRecordedDocument().getDocument().close();
		}
	}

	public void undo() throws RecordingEditException {
		if (!hasUndoActions()) {
			return;
		}

		if (getRecordedEvents().hasUndoActions()) {
			getRecordedEvents().undo();
		}
		if (getRecordedDocument().hasUndoActions()) {
			getRecordedDocument().undo();
		}
		if (getRecordedAudio().hasUndoActions()) {
			getRecordedAudio().undo();
		}
		if (getRecordingHeader().hasUndoActions()) {
			getRecordingHeader().undo();
		}

		fireChangeEvent(Content.ALL);
	}

	public void redo() throws RecordingEditException {
		if (!hasRedoActions()) {
			return;
		}

		if (getRecordedEvents().hasRedoActions()) {
			getRecordedEvents().redo();
		}
		if (getRecordedDocument().hasRedoActions()) {
			getRecordedDocument().redo();
		}
		if (getRecordedAudio().hasRedoActions()) {
			getRecordedAudio().redo();
		}
		if (getRecordingHeader().hasRedoActions()) {
			getRecordingHeader().redo();
		}

		fireChangeEvent(Content.ALL);
	}

	public boolean hasUndoActions() {
		return getRecordingHeader().hasUndoActions() ||
				getRecordedEvents().hasUndoActions() ||
				getRecordedDocument().hasUndoActions() ||
				getRecordedAudio().hasUndoActions();
	}

	public boolean hasRedoActions() {
		return getRecordingHeader().hasRedoActions() ||
				getRecordedEvents().hasRedoActions() ||
				getRecordedDocument().hasRedoActions() ||
				getRecordedAudio().hasRedoActions();
	}

	public int getStateHash() {
		return Objects.hash(getRecordingHeader().getStateHash(),
				getRecordedEvents().getStateHash(),
				getRecordedDocument().getStateHash(),
				getRecordedAudio().getStateHash());
	}

	public void addRecordingChangeListener(RecordingChangeListener listener) {
		listeners.add(listener);
	}

	public void removeRecordingChangeListener(RecordingChangeListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Do not run this method in the UI Thread.
	 * Running it in the UI Thread might lead to freezes of the UI.
	 */
	public void fireChangeEvent(Content contentType) {
		fireChangeEvent(contentType, null);
	}


	/**
	 * Do not run this method in the UI Thread.
	 * Running it in the UI Thread might lead to freezes of the UI.
	 */
	public void fireChangeEvent(Content contentType, Interval<Double> duration) {
		RecordingChangeEvent event = new RecordingChangeEvent(this, contentType);
		event.setDuration(duration);

		for (RecordingChangeListener listener : listeners) {
			listener.recordingChanged(event);
		}
	}

	/**
	 * Find the insert point in the event stream.
	 *
	 * @return the recorded page index at which to insert other recorded pages.
	 */
	public int getPageIndex(int startTime, int snapToPageMargin) {
		List<RecordedPage> recPages = getRecordedEvents().getRecordedPages();
		Interval<Integer> pageInterval = new Interval<>();

		RecordedPage lastPage = new RecordedPage();
		lastPage.setTimestamp(Integer.MAX_VALUE);

		for (int i = 0; i < recPages.size(); i++) {
			RecordedPage page = recPages.get(i);
			RecordedPage nextPage = i < recPages.size() - 1 ? recPages.get(i + 1) : lastPage;

			pageInterval.set(page.getTimestamp(), nextPage.getTimestamp());

			if (Math.abs(nextPage.getTimestamp() - startTime) < snapToPageMargin) {
				return i + 1;
			} else if (pageInterval.contains(startTime)) {
				return i;
			}
		}

		return -1;
	}

	private int getToolDemoRecordingsByteLength() {
		//For the initial count on how many bytes this is.
		int totalLength = 0;

		for (ToolDemoRecording toolDemoRecording : toolDemoRecordings) {
			int length = toolDemoRecording.fileName.getBytes(StandardCharsets.UTF_8).length;

			// 2x Long (8 byte) + 4 byte name length + N bytes of string
			totalLength += 20 + length;
		}

		return totalLength;
	}
}
