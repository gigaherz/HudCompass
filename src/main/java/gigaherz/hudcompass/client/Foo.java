package gigaherz.hudcompass.client;

public class Foo
{
    private int fooPrivate;

    {
        new Bar().barPrivate = 1;
    }

    public class Bar {
        private int barPrivate;
    }
}
