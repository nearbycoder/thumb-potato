import Foundation

enum HTTPError: LocalizedError {
    case status(Int)
    case missingKey
    case notFound

    var errorDescription: String? {
        switch self {
        case .status(401): "The RAWG key was rejected. Check it in Settings."
        case .status(404), .notFound: "That game couldn't be found."
        case .status(429): "Too many requests. Try again in a moment."
        case .status(let code): "The server returned an error (\(code))."
        case .missingKey: "Add a RAWG API key in Settings to use this."
        }
    }
}

enum HTTP {
    static let userAgent = "ThumbPotato/1.0 (https://github.com/nearbycoder/thumb-potato)"

    static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.urlCache = URLCache(memoryCapacity: 32 << 20, diskCapacity: 512 << 20)
        config.requestCachePolicy = .useProtocolCachePolicy
        config.timeoutIntervalForRequest = 20
        // CheapShark and Wikimedia reject generic agents.
        config.httpAdditionalHeaders = ["User-Agent": userAgent]
        return URLSession(configuration: config)
    }()

    static func data(_ url: URL, headers: [String: String] = [:]) async throws -> Data {
        var request = URLRequest(url: url)
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw HTTPError.status(http.statusCode)
        }
        return data
    }

    static func get<T: Decodable>(_ url: URL, as type: T.Type = T.self, headers: [String: String] = [:],
                                  decoder: JSONDecoder = JSONDecoder()) async throws -> T {
        try decoder.decode(T.self, from: try await data(url, headers: headers))
    }

    static func url(_ base: String, _ path: String, _ query: [String: String?] = [:]) -> URL {
        var components = URLComponents(string: base + path)!
        let items = query.compactMap { key, value in value.map { URLQueryItem(name: key, value: $0) } }
        if !items.isEmpty { components.queryItems = items.sorted { $0.name < $1.name } }
        return components.url!
    }
}

enum DateParsing {
    static let day: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    /// Steam's "Sep 17, 2020" / "17 Sep, 2020" / "Q1 2027" / "Coming soon".
    private static let steamFormats: [String] = ["MMM d, yyyy", "d MMM, yyyy", "MMMM d, yyyy", "MMM yyyy", "MMMM yyyy", "yyyy"]

    static func date(_ string: String?) -> Date? {
        guard let string, !string.isEmpty else { return nil }
        if string.count <= 10, let d = day.date(from: String(string.prefix(10))) { return d }
        return Backup.parseISO(string) ?? day.date(from: String(string.prefix(10)))
    }

    static func year(_ string: String?) -> Int? {
        guard let string, string.count >= 4 else { return nil }
        return Int(string.prefix(4))
    }

    static func dayString(_ date: Date) -> String { day.string(from: date) }

    /// Parses a Steam store release date, returning the date and how precise it is.
    static func steam(_ string: String?) -> (Date, DatePrecision)? {
        guard let string = string?.trimmingCharacters(in: .whitespaces), !string.isEmpty else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        for format in steamFormats {
            f.dateFormat = format
            if let date = f.date(from: string) {
                let precision: DatePrecision = format.contains("d") ? .day : (format.contains("M") ? .month : .year)
                return (date, precision)
            }
        }
        return nil
    }
}
