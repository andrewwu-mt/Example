package com.reuters.rfa.example.utility;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.reuters.rfa.config.ConfigDb;

/**
 * Utility class which takes command line arguments from a
 * <code>main(String argv[])</code> and converts them into a database.
 * <p>
 * Command line options must start with a '-' followed by a variable name. Each
 * command line variable must include a single sub-option. For example:<br>
 * <code>   "-sessionName myNamespace::mySession"</code><br>
 * would be a variable with name "sessionName" and a value of
 * "myNamespace::mySession"
 * <p>
 * Methods are provided for access variables with and without default values.
 * <p>
 * "-help &lt;bool&gt;" is added automatically, with a default value of
 * <code>false</code>
 * 
 * @since RFAJ 6.0
 */
public final class CommandLine
{
    private static ConfigDb ConfigDb;

    // for random option lookup when parsing command line
    private static Map<String, Option> Options = Collections
            .synchronizedMap(new HashMap<String, Option>());

    // maintains order so help text can be constructed Can contain either
    // Options or Strings
    private static List<Object> OptionsAndHelpText = Collections
            .synchronizedList(new LinkedList<Object>());

    private static String ArgumentPrefix = "-";
    private static String ProgramName = " ";
    private static String ProgramDesc = " ";

    static
    {
        addOption("help", false, "Display help information and exit");
    }

    /**
     * Specify a string command line option (e.g. -option value).
     * 
     * @param arg option name
     * @param defaultValue String default
     * @param description explanation of the option
     */
    public static void addOption(String arg, String defaultValue, String description)
    {
        Option option = new Option(arg, defaultValue, description);
        Options.put(arg, option);
        OptionsAndHelpText.add(option);
    }

    /**
     * Specify an integer command line option (e.g. -option 10).
     * 
     * @param arg option name
     * @param defaultValue int default
     * @param description explanation of the option
     */
    public static void addOption(String arg, int defaultValue, String description)
    {
        addOption(arg, String.valueOf(defaultValue), description);
    }

    /**
     * Specify a boolean command line option (e.g. -option true).
     * 
     * @param arg option name
     * @param defaultValue boolean default
     * @param description explanation of the option
     */
    public static void addOption(String arg, boolean defaultValue, String description)
    {
        addOption(arg, String.valueOf(defaultValue), description);
    }

    /**
     * Override the defaultValue for previously added String option.
     * 
     * @param varName option to override its default
     * @param defaultValue new defaultValue
     * @see #addOption(String, String, String)
     */
    public static void changeDefault(String varName, String defaultValue)
    {
        Option option = (Option)Options.get(varName);
        option.defaultValue = defaultValue;
    }

    /**
     * Override the defaultValue for previously added boolean option.
     * 
     * @param varName option to override its default
     * @param defaultValue new defaultValue
     * @see #addOption(String, boolean, String)
     */
    public static void changeDefault(String varName, boolean defaultValue)
    {
        changeDefault(varName, String.valueOf(defaultValue));
    }

    /**
     * Override the defaultValue for previously added int option.
     * 
     * @param varName option to override its default
     * @param defaultValue new defaultValue
     * @see #addOption(String, int, String)
     */
    public static void changeDefault(String varName, int defaultValue)
    {
        changeDefault(varName, String.valueOf(defaultValue));
    }

    /**
     * Append any text, such as divisions or blank lines, to the help text.
     * 
     * @param helpString generic text to add to help string
     */
    public synchronized static void appendHelpString(String helpString)
    {
        OptionsAndHelpText.add(helpString);
    }

    /**
     * @return option help text generated from all added options and from {
     *         {@link #appendHelpString(String)}
     */
    public synchronized static String optionHelpString()
    {
        StringBuilder helpString = new StringBuilder(OptionsAndHelpText.size() * 80);
        for (Iterator<Object> iter = OptionsAndHelpText.iterator(); iter.hasNext();)
        {
            Object next = iter.next();
            helpString.append(next.toString());
        }
        return helpString.toString();
    }

    /**
     * @param argv arguments from <code>main()</code>
     */
    public static void setArguments(String[] argv)
    {
        // format: -parameter1 value1 -parameter2 value2 ...
        ConfigDb = new ConfigDb();
        load(argv);
    }

    /**
     * Get the input configuration as a String suitable for printing
     * 
     * @return String value of the configuration
     */
    public static String getConfiguration()
    {
        Set<Map.Entry<String, Option>> set = Options.entrySet();
        Iterator<Entry<String, Option>> i = set.iterator();
        StringBuilder configString = new StringBuilder(Options.size() * 80);

        while (i.hasNext())
        {
            Map.Entry<String, Option> me = (Map.Entry<String, Option>)i.next();

            String keyName = (String)me.getKey();
            configString.append(keyName);
            configString.append(" = ");
            configString.append(variable(keyName));
            configString.append(";\n");
        }

        return (configString.toString());
    }

    private CommandLine()
    {
    }

    private static void load(String[] argv)
    {
        int argCount = argv.length;
        for (int cnt = 0; cnt < argCount; cnt++)
        {
            // current string must start with "-"
            if ((argv[cnt] == null) || (!argv[cnt].startsWith(ArgumentPrefix)))
                continue;

            // strip off leading "-"
            String propName = argv[cnt].substring(ArgumentPrefix.length(), argv[cnt].length());
            cnt++;

            // following string must NOT start with "-"
            if ((!(cnt < argCount)) || (argv[cnt] == null)
                    || (argv[cnt].startsWith(ArgumentPrefix)))
                continue;
            String propValue = argv[cnt];
            ConfigDb.addVariable(propName, propValue);
        }

        if (booleanVariable("help"))
        {
            System.out.println(ProgramDesc);
            System.out.println();
            System.out.println(ProgramName + " supports the following commandline options:");
            System.out.println();
            System.out.println("     Name                Default Value                           Description");
            System.out.println("     ====                =============                           ===========");
            System.out.println(optionHelpString());
            System.exit(1);
        }
    }

    /**
     * @param varName required command line variable (without the '-')
     * @return String value of the variable
     * @throws NullPointerException if required variable was not in the command
     *             line arguments passed in to the constructor
     */
    static public String variable(String varName)
    {
        Option option = (Option)Options.get(varName);
        return ConfigDb.variable(null, varName, option.defaultValue);
    }

    /**
     * @param varName required command line variable (without the '-')
     * @return int value of the variable. If the variable was not in the command
     *         line, return the defaultValue.
     * @throws NullPointerException if required variable was not in the command
     *             line arguments
     */
    static public int intVariable(String varName)
    {
        Option option = (Option)Options.get(varName);
        return Integer.parseInt(ConfigDb.variable(null, varName, option.defaultValue));
    }

    /**
     * @param varName required command line variable (without the '-')
     * @return String value of the variable. If the variable was not in the
     *         command line, return the defaultValue.
     * @throws NullPointerException if required variable was not in the command
     *             line arguments
     */
    static public boolean booleanVariable(String varName)
    {
        Option option = (Option)Options.get(varName);
        return Boolean.valueOf(ConfigDb.variable(null, varName, option.defaultValue))
                .booleanValue();
    }

    /**
     * @param progname Program name for the help string.
     */
    public static void setProgramName(String progname)
    {
        ProgramName = progname;
    }

    /**
     * @param progdesc Program description for the help string.
     */
    public static void setProgramDesc(String progdesc)
    {
        ProgramDesc = progdesc;
    }

    static class Option
    {
        public Option(String a, String dv, String d)
        {
            arg = a;
            defaultValue = dv;
            description = d;
        }

        public String toString()
        {
            int i, spaces;
            boolean longParam = false;
            StringBuilder buf = new StringBuilder(100);
            buf.append("     ");
            buf.append(arg);
            spaces = 20 - arg.length();
            if (spaces <= 0)
            {
                spaces = 35 - arg.length();
                longParam = true;
            }
            for (i = 0; i < spaces; i++)
                buf.append(" ");
            // Check for defaultValue of null
            if (defaultValue == null)
            {
                buf.append("null"); // append the string null
                if (longParam)
                {
                    spaces = 21 - defaultValue.length();
                }
                else
                    spaces = 36; // which is 40 - length of the string "null"
            }
            else
            {
                buf.append(defaultValue);
                if (longParam)
                {
                    spaces = 25 - defaultValue.length();
                }
                else
                    spaces = 40 - defaultValue.length();
            }
            for (i = 0; i < spaces; i++)
                buf.append(" ");
            buf.append(description);
            buf.append('\n');
            return buf.toString();
        }

        final String arg;
        String defaultValue;
        final String description;
    }

    // FUTURE - enhancement ideas for example CommandLine
    // - add 'isRequired' to Option
    // - verify if all required options are present, print help (and missing
    // params) if they are not
    // - check for unknown parameters (dump help or just ignore)
    // - validate parameter values by type: bool, int or string
    // - add ranges to Options: int ranges or string[] for string (i.e. 'OMM'
    // and 'MarketData')

}
