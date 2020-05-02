package gigaherz.hudcompass.icons;

public interface IIconData<T extends IIconData<T>>
{
    IconDataSerializer<T> getSerializer();
}
