/**
 * Copyright 2018 StrongJoshua (strongjoshua@hotmail.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.strongjoshua.console;

import java.util.ArrayList;
import java.util.Collections;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.reflect.Annotation;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Method;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.strongjoshua.console.annotation.ConsoleDoc;

/** @author Eric */
public abstract class AbstractConsole implements Console, Disposable {
	protected final Log log;
	protected CommandExecutor exec;
	protected boolean logToSystem;

	protected boolean disabled;

	protected boolean executeHiddenCommands = true;
	protected boolean displayHiddenCommands = false;
	protected boolean consoleTrace = false;

	public AbstractConsole () {
		log = new Log();
	}

	@Override
	public void setLoggingToSystem (Boolean log) {
		this.logToSystem = log;
	}

	@Override
	public void log (String msg, LogEntryType type) {
		log.addEntry(msg, type);

		if (logToSystem) {
			if (type == LogEntryType.ERROR)
				System.err.println("> " + msg);
			else
				System.out.println("> " + msg);
		}
	}

	@Override
	public void log (String msg) {
		this.log(msg, LogEntryType.DEFAULT);
	}

	@Override
	public void log (Throwable exception, LogEntryType type) {
		this.log(ConsoleUtils.exceptionToString(exception), type);
	}

	@Override
	public void log (Throwable exception) {
		this.log(exception, LogEntryType.ERROR);
	}

	@Override
	public void printLogToFile (String file) {
		this.printLogToFile(Gdx.files.local(file));
	}

	@Override
	public void printLogToFile (FileHandle fh) {
		if (log.printToFile(fh)) {
			log("Successfully wrote logs to file.", LogEntryType.SUCCESS);
		} else {
			log("Unable to write logs to file.", LogEntryType.ERROR);
		}
	}

	@Override
	public boolean isDisabled () {
		return disabled;
	}

	@Override
	public void setDisabled (boolean disabled) {
		this.disabled = disabled;
	}

	@Override
	public void setCommandExecutor (CommandExecutor commandExec) {
		exec = commandExec;
		exec.setConsole(this);
	}

	@Override
	public void execCommand (String command) {
		if (disabled) return;

		log(command, LogEntryType.COMMAND);

		String[] parts = command.split(" ");
		String methodName = parts[0];
		String[] sArgs = null;
		if (parts.length > 1) {
			sArgs = new String[parts.length - 1];
			for (int i = 1; i < parts.length; i++) {
				sArgs[i - 1] = parts[i];
			}
		}

		Class<? extends CommandExecutor> clazz = exec.getClass();
		Method[] methods = ClassReflection.getMethods(clazz);
		Array<Integer> possible = new Array<Integer>();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			if (method.getName().equalsIgnoreCase(methodName) && ConsoleUtils.canExecuteCommand(this, method)) {
				possible.add(i);
			}
		}

		if (possible.size <= 0) {
			log("No such method found.", LogEntryType.ERROR);
			return;
		}

		int size = possible.size;
		int numArgs = sArgs == null ? 0 : sArgs.length;
		for (int i = 0; i < size; i++) {
			Method m = methods[possible.get(i)];
			Class<?>[] params = m.getParameterTypes();
			if (numArgs == params.length) {
				try {
					Object[] args = null;

					try {
						if (sArgs != null) {
							args = new Object[numArgs];

							for (int j = 0; j < params.length; j++) {
								Class<?> param = params[j];
								final String value = sArgs[j];

								if (param.equals(String.class)) {
									args[j] = value;
								} else if (param.equals(Boolean.class) || param.equals(boolean.class)) {
									args[j] = Boolean.parseBoolean(value);
								} else if (param.equals(Byte.class) || param.equals(byte.class)) {
									args[j] = Byte.parseByte(value);
								} else if (param.equals(Short.class) || param.equals(short.class)) {
									args[j] = Short.parseShort(value);
								} else if (param.equals(Integer.class) || param.equals(int.class)) {
									args[j] = Integer.parseInt(value);
								} else if (param.equals(Long.class) || param.equals(long.class)) {
									args[j] = Long.parseLong(value);
								} else if (param.equals(Float.class) || param.equals(float.class)) {
									args[j] = Float.parseFloat(value);
								} else if (param.equals(Double.class) || param.equals(double.class)) {
									args[j] = Double.parseDouble(value);
								}
							}
						}
					} catch (Exception e) {
						// Error occurred trying to parse parameter, continue
						// to next function
						continue;
					}

					m.setAccessible(true);
					m.invoke(exec, args);
					return;
				} catch (ReflectionException e) {
					String msg = e.getMessage();
					if (msg == null || msg.length() <= 0) {
						msg = "Unknown Error";
						e.printStackTrace();
					}
					log(msg, LogEntryType.ERROR);
					if (consoleTrace) {
						log(e, LogEntryType.ERROR);
					}
					return;
				}
			}
		}

		log("Bad parameters. Check your code.", LogEntryType.ERROR);
	}

	private ArrayList<Method> getAllMethods () {
		ArrayList<Method> methods = new ArrayList<Method>();
		Class c = exec.getClass();
		while (c != Object.class) {
			Collections.addAll(methods, ClassReflection.getDeclaredMethods(c));
			c = c.getSuperclass();
		}
		return methods;
	}

	@Override
	public void printCommands () {
		for (Method m : getAllMethods()) {
			if (m.isPublic() && ConsoleUtils.canDisplayCommand(this, m)) {
				String s = "";
				s += m.getName();
				s += " : ";

				Class<?>[] params = m.getParameterTypes();
				for (int i = 0; i < params.length; i++) {
					s += params[i].getSimpleName();
					if (i < params.length - 1) {
						s += ", ";
					}
				}

				log(s);
			}
		}
	}

	@Override
	public void printHelp (String command) {
		boolean found = false;
		for (Method m : getAllMethods()) {
			if (m.getName().equals(command)) {
				found = true;
				StringBuilder sb = new StringBuilder();
				sb.append(m.getName()).append(": ");
				Annotation annotation = m.getDeclaredAnnotation(ConsoleDoc.class);
				if (annotation != null) {
					ConsoleDoc doc = annotation.getAnnotation(ConsoleDoc.class);
					sb.append(doc.description());
					Class<?>[] params = m.getParameterTypes();
					for (int i = 0; i < params.length; i++) {
						sb.append("\n");
						for (int j = 0; j < m.getName().length() + 2; j++)
							// using spaces this way works with monotype fonts
							sb.append(" ");
						sb.append(params[i].getSimpleName()).append(": ");
						if (i < doc.paramDescriptions().length) sb.append(doc.paramDescriptions()[i]);
					}
				} else {
					Class<?>[] params = m.getParameterTypes();
					for (int i = 0; i < params.length; i++) {
						sb.append(params[i].getSimpleName());
						if (i < params.length - 1) {
							sb.append(", ");
						}
					}
				}

				log(sb.toString());
			}
		}

		if (!found) log("Command does not exist.");
	}

	@Override
	public void setExecuteHiddenCommands (boolean enabled) {
		executeHiddenCommands = enabled;
	}

	@Override
	public boolean isExecuteHiddenCommandsEnabled () {
		return executeHiddenCommands;
	}

	@Override
	public void setDisplayHiddenCommands (boolean enabled) {
		displayHiddenCommands = enabled;
	}

	@Override
	public boolean isDisplayHiddenCommandsEnabled () {
		return displayHiddenCommands;
	}

	@Override
	public void setConsoleStackTrace (boolean enabled) {
		this.consoleTrace = enabled;
	}

	@Override
	public void setMaxEntries (int numEntries) {
	}

	@Override
	public void clear () {
	}

	@Override
	public void setSize (int width, int height) {
	}

	@Override
	public void setSizePercent (float wPct, float hPct) {
	}

	@Override
	public void setPosition (int x, int y) {
	}

	@Override
	public void setPositionPercent (float xPosPct, float yPosPct) {
	}

	@Override
	public void resetInputProcessing () {
	}

	@Override
	public InputProcessor getInputProcessor () {
		return null;
	}

	@Override
	public void draw () {
	}

	@Override
	public void refresh () {
	}

	@Override
	public void refresh (boolean retain) {
	}

	@Override
	public int getDisplayKeyID () {
		return 0;
	}

	@Override
	public void setDisplayKeyID (int code) {
	}

	@Override
	public boolean hitsConsole (float screenX, float screenY) {
		return false;
	}

	@Override
	public void dispose () {
	}

	@Override
	public boolean isVisible () {
		return false;
	}

	@Override
	public void setVisible (boolean visible) {
	}

	@Override
	public void select () {
	}

	@Override
	public void deselect () {
	}

	@Override
	public void setTitle (String title) {
	}

	@Override
	public void setHoverAlpha (float alpha) {
	}

	@Override
	public void setNoHoverAlpha (float alpha) {
	}

	@Override
	public void setHoverColor (Color color) {
	}

	@Override
	public void setNoHoverColor (Color color) {
	}

	@Override
	public void enableSubmitButton (boolean enable) {
	}

	@Override
	public void setSubmitText (String text) {
	}

	@Override
	public Window getWindow () {
		return null;
	}
}
