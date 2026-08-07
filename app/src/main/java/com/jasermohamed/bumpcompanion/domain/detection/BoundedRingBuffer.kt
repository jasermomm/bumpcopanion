package com.jasermohamed.bumpcompanion.domain.detection

class BoundedRingBuffer<T>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val deque = ArrayDeque<T>(capacity)

    val size: Int get() = deque.size
    fun isEmpty(): Boolean = deque.isEmpty()

    fun add(item: T) {
        if (deque.size == capacity) deque.removeFirst()
        deque.add(item)
    }

    fun clear() = deque.clear()
    fun toList(): List<T> = deque.toList()
    fun lastOrNull(): T? = deque.lastOrNull()
    fun firstOrNull(): T? = deque.firstOrNull()
    fun filter(predicate: (T) -> Boolean): List<T> = deque.filter(predicate)
}
