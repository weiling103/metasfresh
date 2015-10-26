package de.metas.migration.cli;

/*
 * #%L
 * de.metas.migration.cli
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.metas.migration.IDatabase;
import de.metas.migration.applier.IScriptsApplierListener;
import de.metas.migration.applier.impl.NullScriptsApplierListener;
import de.metas.migration.executor.IScriptExecutorFactory;
import de.metas.migration.impl.AbstractScriptsApplierTemplate;
import de.metas.migration.impl.SQLDatabase;
import de.metas.migration.scanner.IFileRef;
import de.metas.migration.scanner.IScriptFactory;
import de.metas.migration.scanner.IScriptScanner;
import de.metas.migration.scanner.IScriptScannerFactory;
import de.metas.migration.scanner.impl.FileRef;
import de.metas.migration.scanner.impl.GloballyOrderedScannerDecorator;

public final class RolloutMigrate
{
	private static final String DEFAULT_SETTINGS_FILENAME = "local_settings.properties";

	private static final transient Logger logger = LoggerFactory.getLogger(RolloutMigrate.class);

	private static final String OPTION_Help = "h";
	private static final String OPTION_RolloutDirectory = "d";
	private static final String DEFAULT_RolloutDirectory = "./..";

	private static final String OPTION_ScriptFile = "f";
	private static final String OPTION_SettingsFile = "s";
	private static final String OPTION_IgnoreErrors = "i";
	private static final String OPTION_JustMarkScriptAsExecuted = "r";

	private final Options options;
	private Properties settings;
	private File rolloutDir;
	private File sqlDir;
	private boolean ignoreErrors;
	private boolean justMarkScriptAsExecuted;
	private String scriptFile;

	public RolloutMigrate()
	{
		super();
		options = RolloutMigrate.createOptions();
	}

	private static final Options createOptions()
	{
		final Options options = new Options();
		// Help
		{
			final Option option = new Option(RolloutMigrate.OPTION_Help, "Print this message and exit");
			option.setArgs(0);
			option.setArgName("Help");
			option.setRequired(false);
			options.addOption(option);
		}
		// Rollout Directory
		{
			final Option option = new Option(RolloutMigrate.OPTION_RolloutDirectory,
					"Directory that contains the rollout package. The tool assumes that the actual SQL scripts are in a folder structure within <RolloutDirectory>/sql/. "
							+ "If omitted, then '" + RolloutMigrate.DEFAULT_RolloutDirectory + "' (i.e. " + new File(RolloutMigrate.DEFAULT_RolloutDirectory).getAbsolutePath() + ") will be used");
			option.setArgs(1);
			option.setArgName("Rollout-Directory");
			option.setRequired(false);
			options.addOption(option);
		}
		// File
		{
			final Option option = new Option(RolloutMigrate.OPTION_ScriptFile, "Only process the given file in the rollout directory");
			option.setArgs(1);
			option.setArgName("File");
			option.setRequired(false);
			options.addOption(option);
		}
		// Settings
		{
			final Option option = new Option(RolloutMigrate.OPTION_SettingsFile,
					"Settings file (e.g. settings_<hostname>.properties). If ommitted, then "
							+ System.getProperty("user.home") + "/" + DEFAULT_SETTINGS_FILENAME + " will be used, where " + System.getProperty("user.home") + " is the current user's home directory");
			option.setArgs(1);
			option.setArgName("Settings file");
			option.setRequired(false);
			options.addOption(option);
		}
		// Ignore database errors
		{
			final Option option = new Option(RolloutMigrate.OPTION_IgnoreErrors, "Ignore database errors. WARNING: Only use if you know what you are doing!");
			option.setArgs(0);
			option.setRequired(false);
			options.addOption(option);
		}
		// Only record script
		{
			final Option option = new Option(RolloutMigrate.OPTION_JustMarkScriptAsExecuted, "Only record script, but don't actually execute. WARNING: Only use if you know what you are doing!");
			option.setArgs(0);
			option.setRequired(false);
			options.addOption(option);
		}

		return options;
	}

	private void log(final String msg)
	{
		final Throwable e = null;
		log(msg, e);
	}

	private void log(final String msg, final Throwable e)
	{
		logger.info(msg, e);
	}

	private final boolean init(final String[] args)
	{
		final CommandLineParser parser = new PosixParser();
		final CommandLine cmd;
		try
		{
			cmd = parser.parse(options, args);
		}
		catch (final Exception e)
		{
			logger.error(e.getLocalizedMessage()
					+ "\n\n" + printHelpToString());

			throw new RuntimeException(e);
		}

		final boolean printHelp = cmd.hasOption(RolloutMigrate.OPTION_Help);
		if (printHelp)
		{
			printHelp(System.out);
			return false;
		}

		final String rolloutDir = cmd.getOptionValue(RolloutMigrate.OPTION_RolloutDirectory, RolloutMigrate.DEFAULT_RolloutDirectory);
		setRolloutDir(rolloutDir);

		final String settingsFile = cmd.getOptionValue(RolloutMigrate.OPTION_SettingsFile);
		if (settingsFile != null)
		{
			setSettingsFile(getRolloutDir(), settingsFile);
		}
		else
		{
			setSettingsFile(new File(System.getProperty("user.home")), DEFAULT_SETTINGS_FILENAME);
		}

		final String scriptFile = cmd.getOptionValue(RolloutMigrate.OPTION_ScriptFile);
		setScriptFile(scriptFile);

		final boolean ignoreErrors = cmd.hasOption(RolloutMigrate.OPTION_IgnoreErrors);
		setIgnoreErrors(ignoreErrors);

		final boolean justMarkScriptAsExecuted = cmd.hasOption(RolloutMigrate.OPTION_JustMarkScriptAsExecuted);
		setJustMarkScriptAsExecuted(justMarkScriptAsExecuted);

		return true;
	}

	private void setRolloutDir(final String rolloutDirname)
	{
		if (rolloutDirname == null || rolloutDirname.trim().isEmpty())
		{
			throw new IllegalArgumentException("Rollout directory not specified");
		}

		rolloutDir = RolloutMigrate.checkDirectory("Rollout directory", new File(rolloutDirname));
		log("Rollout directory: " + rolloutDir);

		final File sqlDir = new File(rolloutDir, "sql");
		setSqlDir(sqlDir);
	}

	private File getRolloutDir()
	{
		if (rolloutDir == null)
		{
			throw new IllegalStateException("Rollout Directory was not configured");
		}
		return rolloutDir;
	}

	private void setJustMarkScriptAsExecuted(final boolean justMarkScriptAsExecuted)
	{
		this.justMarkScriptAsExecuted = justMarkScriptAsExecuted;
		log("Just mark the script as executed: " + this.justMarkScriptAsExecuted);
	}

	private boolean isJustMarkScriptAsExecuted()
	{
		return justMarkScriptAsExecuted;
	}

	private void setIgnoreErrors(final boolean ignoreErrors)
	{
		this.ignoreErrors = ignoreErrors;
		log("Ignore errors: " + this.ignoreErrors);

		if (this.ignoreErrors)
		{
			throw new UnsupportedOperationException("'Ignore errors' option is not implemented at the moment");
		}
	}

	private void setScriptFile(final String scriptFile)
	{
		this.scriptFile = scriptFile;
		log("Script file: " + this.scriptFile);
	}

	private void setSettingsFile(final File dir, final String settingsFilename)
	{
		final File settingsFile = new File(dir, settingsFilename);
		final Properties settings = new Properties();

		FileInputStream in = null;
		try
		{
			in = new FileInputStream(settingsFile);
			settings.load(in);
		}
		catch (final IOException e)
		{
			throw new RuntimeException("Cannot load " + settingsFile, e);
		}
		finally
		{
			if (in != null)
			{
				try
				{
					in.close();
				}
				catch (final IOException e)
				{
				}
			}
		}

		log("Settings file: " + settingsFilename);
		this.settings = settings;
	}

	private String getProperty(final String propertyName, final String defaultValue)
	{
		if (settings == null)
		{
			throw new IllegalStateException("Settings were not configured");
		}

		return settings.getProperty(propertyName, defaultValue);
	}

	private void setSqlDir(final File sqlDir)
	{
		this.sqlDir = RolloutMigrate.checkDirectory("SQL Directory", sqlDir);
		log("SQL directory: " + this.sqlDir);
	}

	private File getSqlDir()
	{
		if (sqlDir == null)
		{
			throw new IllegalStateException("SQL Directory was not configured");
		}
		return sqlDir;
	}

	private static final File checkDirectory(final String name, final File dir)
	{
		if (!dir.exists())
		{
			throw new IllegalArgumentException(name + " '" + dir + "' does not exists");
		}

		final File dirAbs;
		try
		{
			dirAbs = dir.getCanonicalFile();
		}
		catch (final IOException e)
		{
			throw new IllegalArgumentException(name + " '" + dir + "' is not accessible", e);
		}

		if (!dirAbs.isDirectory())
		{
			throw new IllegalArgumentException(name + " '" + dirAbs + "' is not a directory");
		}
		if (!dirAbs.canRead())
		{
			throw new IllegalArgumentException(name + " '" + dirAbs + "' is not readable");
		}

		return dirAbs;
	}

	public final void run()
	{
		final long ts = System.currentTimeMillis();
		try
		{
			run0();
		}
		finally
		{
			final long ts2 = System.currentTimeMillis();
			log("Duration: " + (ts2 - ts) + "ms (" + new Date(ts2) + ")");
			log("Done.");
		}

	}

	private void run0()
	{
		final AbstractScriptsApplierTemplate scriptApplier = new AbstractScriptsApplierTemplate()
		{
			@Override
			protected IScriptFactory createScriptFactory()
			{
				return new RolloutDirScriptFactory();
			}

			@Override
			protected void configureScriptExecutorFactory(final IScriptExecutorFactory scriptExecutorFactory)
			{
				scriptExecutorFactory.setDryRunMode(isJustMarkScriptAsExecuted());
			}

			@Override
			protected IScriptScanner createScriptScanner(final IScriptScannerFactory scriptScannerFactory)
			{
				final String fileName;
				if (scriptFile != null && !scriptFile.isEmpty())
				{
					if (new File(scriptFile).exists())
					{
						fileName = scriptFile;
					}
					else
					{
						fileName = getSqlDir().getAbsolutePath() + File.separator + scriptFile;
					}
				}
				else
				{
					fileName = getSqlDir().getAbsolutePath();
				}
				
				final IFileRef fileRef = new FileRef(new File(fileName));
				final IScriptScanner scriptScanner = scriptScannerFactory.createScriptScanner(fileRef);

				return new GloballyOrderedScannerDecorator(scriptScanner);
			}

			@Override
			protected IScriptsApplierListener createScriptsApplierListener()
			{
				return NullScriptsApplierListener.instance;
			}

			@Override
			protected IDatabase createDatabase()
			{
				final String dbType = getProperty("ADEMPIERE_DB_TYPE", "postgresql");
				final String dbHostname = getProperty("ADEMPIERE_DB_SERVER", "localhost");
				final String dbPort = getProperty("ADEMPIERE_DB_PORT", "5432");
				final String dbName = getProperty("ADEMPIERE_DB_NAME", "adempiere");
				final String dbUser = getProperty("ADEMPIERE_DB_USER", "adempiere");
				final String dbPassword = getProperty("ADEMPIERE_DB_PASSWORD",
						// Default value is null because in case is not configured we shall use other auth methods
						IDatabase.PASSWORD_NA
						);
				return new SQLDatabase(dbType, dbHostname, dbPort, dbName, dbUser, dbPassword);
			}
		};

		scriptApplier.run();
	}

	public final void printHelp(final PrintStream out)
	{
		final PrintWriter writer = new PrintWriter(out);
		final String commandName = "RolloutMigrate";
		final String header = "Util to apply ADempiere migration scripts to a POstgresSQL database. The database settings are read from a settings (properties) file.";
		final String footer = "\nHint: The tool checks if a script has already been applied";

		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				writer, // output
				200, // width,
				commandName, // cmdLineSyntax
				header, // header,
				options, // options
				4, // leftPad,
				4, // descPad,
				footer, // footer,
				true // autoUsage
				);

		writer.flush();
	}

	public final String printHelpToString()
	{
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintStream out = new PrintStream(baos);
		printHelp(out);

		final String content = baos.toString();
		return content;
	}

	public static final void main(final String[] args)
	{
		logger.info("RolloutMigrate (" + Version.instance + ")");

		final RolloutMigrate main = new RolloutMigrate();

		if (main.init(args))
		{
			main.run();
		}
	}
}
