//
//  CalendarExtensions.swift
//  Goose
//
//  Created to fix timezone issues with session date grouping
//

import Foundation

extension Calendar {
    /// Returns a Calendar configured to use UTC timezone
    /// This ensures date comparisons match the API's UTC timestamps
    static var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }
}

extension Date {
    /// Returns the start of day for this date in UTC timezone
    var startOfDayUTC: Date {
        return Calendar.utc.startOfDay(for: self)
    }
    
    /// Checks if this date is in the same day as another date, using UTC timezone
    func isSameDayUTC(as otherDate: Date) -> Bool {
        return Calendar.utc.isDate(self, inSameDayAs: otherDate)
    }
    
    /// Checks if this date is today in UTC timezone
    var istodayUTC: Bool {
        return Calendar.utc.isDateInToday(self)
    }
    
    /// Checks if this date is yesterday in UTC timezone
    var isYesterdayUTC: Bool {
        return Calendar.utc.isDateInYesterday(self)
    }
}
