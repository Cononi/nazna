package io.github.cononi;

import io.github.cononi.core.NaznaApplication;
import io.github.cononi.core.annotation.NaznaServerApplication;

@NaznaServerApplication
public class NaznaServer {
    public static void main(String[] args) {
        NaznaApplication container = new NaznaApplication();
        container.run(NaznaServer.class,args);
    }
}