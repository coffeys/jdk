package loggerfinder;

import java.lang.*;
import java.util.*;

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
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            System.out.println("TEST LOGGER: " + msg);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            System.out.println("TEST LOGGER: " + format);
        }
    }
}