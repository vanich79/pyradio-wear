package com.pyradio.wear.resolver

import com.pyradio.wear.model.FailureReason

/** Чем закончилась попытка превратить ссылку из плейлиста в адрес потока. */
sealed interface Resolution {

    /**
     * Нашлись адреса, которые можно отдать плееру.
     *
     * [urls] — по убыванию предпочтения, в том порядке, в каком они лежали в
     * `.pls`. Их несколько не для красоты: SomaFM раздаёт в одном файле три-четыре
     * зеркала, и когда первое молчит, играет второе. Список из одного элемента —
     * нормальный случай, а не признак бедности.
     *
     * [hls] означает, что адрес — манифест HLS, и разворачивать его нельзя:
     * это работа плеера, а не наша.
     */
    data class Streams(val urls: List<String>, val hls: Boolean) : Resolution

    data class Failure(val reason: FailureReason) : Resolution
}
