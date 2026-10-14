package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.PlaylistItem

fun PlaylistItem.getEffectiveTitleLogoUrl(): String? {
    if (!titleLogoUrl.isNullOrEmpty()) return titleLogoUrl.toOriginalTmdbUrl()
    if (!clearLogoUrl.isNullOrEmpty()) return clearLogoUrl.toOriginalTmdbUrl()
    if (!logo.isNullOrEmpty() && (logo.endsWith(".png", ignoreCase = true) || logo.contains("clearlogo", ignoreCase = true) || logo.contains("logo", ignoreCase = true))) {
        return logo.toOriginalTmdbUrl()
    }

    val matched = when {
        title.contains("Cobrador", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.png"
        title.contains("Passageiro do Mal", ignoreCase = true) || title.contains("Passenger", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/2wdMrwQHQ28q69IMvqmc3SUAL8W.png"
        title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png"
        title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rpNau5FSkTtAVCVORJpqUmSXAHJ.png"
        title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/msFWTNrFwfjsHKnMxDUexR9uofh.png"
        title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/megWWuC8tSODx90i0gP0fVM1wVo.png"
        title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png"
        title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/jpLOFTHacWoxCTCLRePVlLHxrbo.png"
        title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/3pzH42BKQ0z30fM9DPwSewvWYLS.png"
        title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/oHP5aXQ4gg15t9EsBhgKENeCKdG.png"
        title.contains("Rua", ignoreCase = true) || title.contains("Oak Street", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/e0HtKXHvWKFiYmzP3G8xLo7jo5B.png"
        title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/iEYvlw4yqSp9Ck6Iz90V31QSD7D.png"
        title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zdzXhbQgjOQzrQ6Yq5kWepYw7MJ.png"
        title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9w0aK6Vs1YqSp13EhrlZVQ4TJ1u.png"
        title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/gAwgHNn9bgNefFEzv2sY6Q2BRVo.png"
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/vIprMXDcEruwpW4aL5PMTQrkljb.png"
        title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png"
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rj1CuaIo5BYr9XxlLztZMnWP3YT.png"
        title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wQ7FFixqLXD2aOqtoiawyz7IbVx.png"
        else -> null
    }

    return (matched ?: logoUrl.takeIf { !it.isNullOrEmpty() } ?: backdropUrl.takeIf { !it.isNullOrEmpty() })?.toOriginalTmdbUrl()
}

fun String.toOriginalTmdbUrl(): String {
    if (this.contains("image.tmdb.org/t/p/")) {
        val filename = this.substringAfterLast("/")
        if (filename.isNotEmpty()) {
            return "https://image.tmdb.org/t/p/w1280/$filename"
        }
    }
    return this
}

fun PlaylistItem.getEffectiveLogoUrl(): String? {
    val url = logoUrl
    if (url.isNullOrEmpty()) {
        val fallback = when {
            title.contains("Cobrador", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg"
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/k334z7zxOJlJvOsOwyRF6HClCvi.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/7WOBeoaXUNGzbm79Umio2CcH941.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/aJVroxZZKReiSlASpODQ23qrdGa.jpg"
            else -> null
        }
        return fallback?.toOriginalTmdbUrl()
    }
    return url.toOriginalTmdbUrl()
}

fun PlaylistItem.getEffectiveDescription(): String {
    if (!description.isNullOrBlank()) return description!!
    return when {
        title.contains("Cobrador", ignoreCase = true) -> "Assombrado pela culpa após a prisão, um ex-cobrador de dívidas corre contra uma doença terminal, retornando ao seu antigo mundo para vingar as vítimas dos agiotas."
        title.contains("Passageiro", ignoreCase = true) -> "Um passageiro misterioso embarca em um voo noturno com segredos mortais que ameaçam todos a bordo."
        title.contains("Obsessão", ignoreCase = true) -> "Presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira em um thriller psicológico eletrizante."
        title.contains("Pradaria", ignoreCase = true) -> "A jornada épica pela sobrevivência nas vastas terras selvagens do oeste americano."
        title.contains("Furious", ignoreCase = true) -> "Velocidade extrema e adrenalina pura em uma corrida contra o tempo."
        title.contains("Moana", ignoreCase = true) -> "Uma jovem corajosa parte em uma missão ousada para salvar seu povo e descobrir sua verdadeira identidade."
        title.contains("Demônio", ignoreCase = true) -> "Forças malévolas despertam em um recanto isolado, desencadeando um terror incontrolável."
        title.contains("Odisseia", ignoreCase = true) -> "Uma jornada épica inspirada no clássico grego através de mares desconhecidos e deuses antigos."
        title.contains("Perder", ignoreCase = true) -> "Dois detetives obstinados entram em um jogo perigoso de gato e rato para desvendar um crime."
        title.contains("Backrooms", ignoreCase = true) -> "Um labirinto infinito de salas amarelas vazias habitadas por uma entidade misteriosa."
        title.contains("Rua", ignoreCase = true) -> "Mistérios sombrios vêm à tona no fim de uma rua aparentemente pacata."
        title.contains("Hope", ignoreCase = true) -> "Uma história comovente de esperança, superação e laços inquebráveis."
        title.contains("Jackass", ignoreCase = true) -> "Manobras insanas e muita diversão com o grupo mais louco da televisão."
        title.contains("Cinzenta", ignoreCase = true) -> "Agentes especiais operam nas fronteiras perigosas da legalidade internacional."
        title.contains("Prazer", ignoreCase = true) -> "Um romance tórrido e perigoso entre um escritor intrigado e uma misteriosa milionária."
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones."
        title.contains("NCIS", ignoreCase = true) -> "Casos criminais complexos envolvendo o pessoal da Marinha americana com investigações de alta tecnologia."
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law", ignoreCase = true) -> "Casos criminais sob a ótica dos detetives policiais e promotores implacáveis."
        title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "A luta diária e implacável de uma família proprietária de terras em Montana para proteger seu império."
        else -> "Uma incrível produção cheia de emoção, mistério e entretenimento de alta qualidade para você assistir agora."
    }
}

fun PlaylistItem.getEffectiveYear(): String {
    if (!year.isNullOrBlank()) return year!!
    return "2026"
}

fun PlaylistItem.getEffectiveGenre(): String {
    if (!genre.isNullOrBlank()) return genre!!
    return when (type.lowercase()) {
        "series" -> "Séries | Drama"
        else -> "Filmes | Lançamentos"
    }
}

fun PlaylistItem.getEffectiveRating(): String {
    if (!rating.isNullOrBlank()) return rating!!
    return "8.2"
}

fun playInVLC(context: Context, url: String, title: String) {
    try {
        val uri = Uri.parse(url)
        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            setPackage("org.videolan.vlc")
            putExtra("title", title)
            putExtra("from_start", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(vlcIntent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Instale o VLC Player para usar esta opção!",
            Toast.LENGTH_LONG
        ).show()

        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(marketIntent)
        } catch (anfe: ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.videolan.vlc"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }
}

fun cleanServerUrl(serverUrl: String): String {
    if (serverUrl.isBlank()) return ""
    var cleaned = serverUrl.trim()
    val removeSuffixes = listOf("/get.php", "/player_api.php", "/xmltv.php")
    for (ending in removeSuffixes) {
        if (cleaned.endsWith(ending)) {
            cleaned = cleaned.substring(0, cleaned.length - ending.length)
        }
    }
    if (cleaned.endsWith("/")) {
        cleaned = cleaned.substring(0, cleaned.length - 1)
    }
    return cleaned
}

fun rewriteUrlWithActiveCredentials(
    originalUrl: String,
    activeServerUrl: String,
    activeUser: String,
    activePass: String
): String {
    if (originalUrl.isBlank()) return ""

    // If it's a sample URL or mock URL, return it as-is
    if (originalUrl.contains("googleapis.com") || originalUrl.contains("sample") || originalUrl.contains("BigBuckBunny")) {
        return originalUrl
    }

    try {
        val uri = Uri.parse(originalUrl)
        val pathSegments = uri.pathSegments

        val cleanServer = cleanServerUrl(activeServerUrl)
        val query = uri.query
        val suffix = if (!query.isNullOrBlank()) "?$query" else ""

        // Pattern 1: /movie/username/password/stream_id.ext -> 4 segments
        // Pattern 2: /series/username/password/stream_id.ext -> 4 segments
        // Pattern 3: /live/username/password/stream_id.ext -> 4 segments
        if (pathSegments.size >= 4) {
            val type = pathSegments[0].lowercase()
            if (type == "movie" || type == "movies" || type == "series" || type == "live") {
                var streamIdWithExt = pathSegments[3]
                if ((type == "movie" || type == "movies") && streamIdWithExt.endsWith(".ts", ignoreCase = true)) {
                    streamIdWithExt = streamIdWithExt.substring(0, streamIdWithExt.length - 3) + ".mp4"
                }
                return "$cleanServer/$type/$activeUser/$activePass/$streamIdWithExt$suffix"
            }
        }

        // Pattern 4: /username/password/stream_id -> exactly 3 segments
        if (pathSegments.size == 3) {
            val streamIdWithExt = pathSegments[2]
            return "$cleanServer/$activeUser/$activePass/$streamIdWithExt$suffix"
        }
    } catch (e: Exception) {
        // Fallback
    }

    return originalUrl
}

fun com.example.data.model.PlaylistItem.getCleanPtBrTitle(): String {
    val raw = this.cleanTitle?.takeIf { it.isNotBlank() } ?: this.title
    if (raw.isBlank()) return ""
    val techRegex = Regex("""(?i)\b(1080p|720p|2160p|4k|hdr|web-?dl|webrip|hdtv|bluray|dublado|dual|legendado|sub|pt-?br|x264|x265|hevc|aac|5\.1|ac3|remux)\b""")
    var cleaned = raw.replace(techRegex, "")
    cleaned = cleaned.replace(Regex("""[\(\[\{].*?[\)\]\}]"""), "")
    cleaned = cleaned.replace(Regex("""\.(mp4|mkv|avi|ts)$""", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("""[-_]+"""), " ")
    cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
    return if (cleaned.isNotBlank()) cleaned else raw
}
