package loggerfinder;

import java.lang.*;
import java.util.*;
import java.util.logging.*;

public class SimpleLoggerFinder extends System.LoggerFinder {
     @Override
     public System.Logger getLogger(String name, Module module) {
         return new NoOpLogger(name);
     }

    private static class NoOpLogger implements System.Logger {
        private final String name;

        public NoOpLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(Level level) {
            return false;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        }
    }
}
