package cn.elvis.monaco.persistence;

public interface Repository<T> {

    void write(T t);
}
