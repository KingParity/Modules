command log {
    perm utils.logs;
    alias logs;
    type player;
    
    search [string:file(s)] [string:search...] {
        run search_logs file(s) search;
        help Performs the specified search operation on the logs. Wildcards are supported in filenames. Search string is a regex.;
    }
    terminate {
    	run terminate_search;
    	help Stops the search.;
    }
    format {
        run show_format;
        help Displays your current log output format with an example result.;
    }
    format_help {
        run show_format_help;
        help Displays all available placeholders for the formatting;
    }
    option_help {
        run show_option_help;
        help Displays all available options.;
    }
    set format [string:format] {
        run set_format format;
        help Sets a new log output format;
    }
    set [string:option] [boolean:state] {
        run set_option option state;
        help Allows you to enable or disable various features such as sumamries, live progress updates, etc...;
    }
}