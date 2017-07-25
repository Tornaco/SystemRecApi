package dev.nick.library.common;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */
@Getter
@Setter
public class Holder<T> {
    private T data;
}
