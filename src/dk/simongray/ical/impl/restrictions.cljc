(ns ^:no-doc dk.simongray.ical.impl.restrictions
  "The restriction tables of iTIP, RFC 5546, section 3: for each method
  and component, which components and properties a message may hold,
  and how many. A presence is \"1\", \"1+\", \"0\", \"0+\" or
  \"0 or 1\". Made by dev/dk/simongray/ical/tables.clj; don't edit it
  by hand.")

(def tables
  "The table of each pair of method and component name, as a map of
  :calendar, the presence of each component of the calendar, and
  :component, the presence of each property and component inside the
  scheduled component."
  {["ADD" :vevent]
   {:calendar  {:vevent "1" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "0+" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "1" :duration "0 or 1"
                :exdate "0" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0" :recurrence-id "0"
                :related-to "0+" :request-status "0" :resources "0+" :rrule "0"
                :sequence "1" :status "0 or 1" :summary "1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0+"}}
   ["ADD" :vjournal]
   {:calendar  {:vevent "0" :vfreebusy "0" :vjournal "1" :vtimezone "0 or 1" :vtodo "0"}
    :component {:attach "0+" :attendee "0" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "1"
                :dtstamp "1" :dtstart "1" :exdate "0" :last-modified "0 or 1"
                :organizer "1" :rdate "0" :recurrence-id "0" :related-to "0+"
                :request-status "0" :rrule "0" :sequence "1" :status "0 or 1"
                :summary "0 or 1" :uid "1" :url "0 or 1" :valarm "0+"}}
   ["ADD" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "1"}
    :component {:attach "0+" :attendee "0+" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "0 or 1" :due "0 or 1"
                :duration "0 or 1" :exdate "0" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "1"
                :rdate "0" :recurrence-id "0" :related-to "0+" :request-status "0"
                :resources "0+" :rrule "0" :sequence "1" :status "0 or 1"
                :summary "1" :uid "1" :url "0 or 1" :valarm "0+"}}
   ["CANCEL" :vevent]
   {:calendar  {:vevent "1+" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "0+" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "0 or 1" :duration "0 or 1"
                :exdate "0+" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0+" :recurrence-id "0 or 1"
                :related-to "0+" :request-status "0" :resources "0+" :rrule "0 or 1"
                :sequence "1" :status "0 or 1" :summary "0 or 1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0"}}
   ["CANCEL" :vjournal]
   {:calendar  {:vevent "0" :vfreebusy "0" :vjournal "1+" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "0" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtstamp "1" :dtstart "0 or 1" :exdate "0+" :last-modified "0 or 1"
                :organizer "1" :rdate "0+" :recurrence-id "0 or 1" :related-to "0+"
                :request-status "0" :rrule "0 or 1" :sequence "1" :status "0 or 1"
                :summary "0 or 1" :uid "1" :url "0 or 1" :valarm "0"}}
   ["CANCEL" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vtimezone "0 or 1" :vtodo "1+"}
    :component {:attach "0+" :attendee "0+" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "0 or 1" :due "0 or 1"
                :duration "0 or 1" :exdate "0+" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "0 or 1"
                :rdate "0+" :recurrence-id "0 or 1" :related-to "0+" :request-status "0"
                :resources "0+" :rrule "0 or 1" :sequence "1" :status "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0"}}
   ["COUNTER" :vevent]
   {:calendar  {:vevent "1" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "0+" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "1" :duration "0 or 1"
                :exdate "0+" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0+" :recurrence-id "0 or 1"
                :related-to "0+" :request-status "0+" :resources "0+" :rrule "0 or 1"
                :sequence "1" :status "0 or 1" :summary "1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0+"}}
   ["COUNTER" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vtimezone "0 or 1" :vtodo "1"}
    :component {:attach "0+" :attendee "1+" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "0 or 1" :due "0 or 1"
                :duration "0 or 1" :exdate "0+" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "1"
                :rdate "0+" :recurrence-id "0 or 1" :related-to "0+" :request-status "0+"
                :resources "0+" :rrule "0 or 1" :sequence "0 or 1" :status "0 or 1"
                :summary "1" :uid "1" :url "0 or 1" :valarm "0+"}}
   ["DECLINECOUNTER" :vevent]
   {:calendar  {:vevent "1+" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "1+" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "0 or 1" :duration "0 or 1"
                :exdate "0+" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0+" :recurrence-id "0 or 1"
                :related-to "0+" :request-status "0+" :resources "0+" :rrule "0 or 1"
                :sequence "1" :status "0 or 1" :summary "0 or 1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0"}}
   ["DECLINECOUNTER" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vtimezone "0+" :vtodo "1"}
    :component {:attach "0+" :attendee "1+" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "0 or 1" :due "0 or 1"
                :duration "0 or 1" :exdate "0+" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "0 or 1"
                :rdate "0+" :recurrence-id "0 or 1" :related-to "0+" :request-status "0+"
                :resources "0+" :rrule "0 or 1" :sequence "1" :status "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0"}}
   ["PUBLISH" :vevent]
   {:calendar  {:vevent "1+" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "0" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0 or 1" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "1" :duration "0 or 1"
                :exdate "0+" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0+" :recurrence-id "0 or 1"
                :related-to "0+" :request-status "0" :resources "0+" :rrule "0 or 1"
                :sequence "0 or 1" :status "0 or 1" :summary "1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0+"}}
   ["PUBLISH" :vfreebusy]
   {:calendar  {:vevent "0" :vfreebusy "1+" :vjournal "0" :vtimezone "0" :vtodo "0"}
    :component {:attendee "0" :comment "0+" :contact "0 or 1" :dtend "1"
                :dtstamp "1" :dtstart "1" :duration "0" :freebusy "0+"
                :organizer "1" :request-status "0" :uid "1" :url "0 or 1"
                :valarm "0"}}
   ["PUBLISH" :vjournal]
   {:calendar  {:vevent "0" :vfreebusy "0" :vjournal "1+" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "0" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "1"
                :dtstamp "1" :dtstart "1" :exdate "0+" :last-modified "0 or 1"
                :organizer "1" :rdate "0+" :recurrence-id "0 or 1" :related-to "0+"
                :request-status "0" :rrule "0 or 1" :sequence "0 or 1" :status "0 or 1"
                :summary "0 or 1" :uid "1" :url "0 or 1" :valarm "0+"}}
   ["PUBLISH" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "1+"}
    :component {:attach "0+" :attendee "0" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "1" :due "0 or 1"
                :duration "0 or 1" :exdate "0+" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "1"
                :rdate "0+" :recurrence-id "0 or 1" :related-to "0+" :request-status "0"
                :resources "0+" :rrule "0 or 1" :sequence "0 or 1" :status "0 or 1"
                :summary "1" :uid "1" :url "0 or 1" :valarm "0+"}}
   ["REFRESH" :vevent]
   {:calendar  {:vevent "1" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0" :attendee "1" :categories "0" :class "0"
                :comment "0+" :contact "0" :created "0" :description "0"
                :dtend "0" :dtstamp "1" :dtstart "0" :duration "0"
                :exdate "0" :geo "0" :last-modified "0" :location "0"
                :organizer "1" :priority "0" :rdate "0" :recurrence-id "0 or 1"
                :related-to "0" :request-status "0" :resources "0" :rrule "0"
                :sequence "0" :status "0" :summary "0" :transp "0"
                :uid "1" :url "0" :valarm "0"}}
   ["REFRESH" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vtimezone "0+" :vtodo "1"}
    :component {:attach "0" :attendee "1" :categories "0" :class "0"
                :comment "0" :completed "0" :contact "0" :created "0"
                :description "0" :dtstamp "1" :dtstart "0" :due "0"
                :duration "0" :exdate "0" :geo "0" :last-modified "0"
                :location "0" :organizer "0" :percent-complete "0" :priority "0"
                :rdate "0" :recurrence-id "0 or 1" :related-to "0" :request-status "0"
                :resources "0" :rrule "0" :sequence "0" :status "0"
                :uid "1" :url "0" :valarm "0"}}
   ["REPLY" :vevent]
   {:calendar  {:vevent "1+" :vfreebusy "0" :vjournal "0" :vtimezone "0 or 1" :vtodo "0"}
    :component {:attach "0+" :attendee "1" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "0 or 1" :duration "0 or 1"
                :exdate "0+" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0+" :recurrence-id "0 or 1"
                :related-to "0+" :request-status "0+" :resources "0+" :rrule "0 or 1"
                :sequence "0 or 1" :status "0 or 1" :summary "0 or 1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0"}}
   ["REPLY" :vfreebusy]
   {:calendar  {:vevent "0" :vfreebusy "1" :vjournal "0" :vtimezone "0" :vtodo "0"}
    :component {:attendee "1" :comment "0+" :contact "0 or 1" :dtend "1"
                :dtstamp "1" :dtstart "1" :duration "0" :freebusy "0+"
                :organizer "1" :request-status "0+" :sequence "0" :uid "1"
                :url "0 or 1" :valarm "0"}}
   ["REPLY" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vtimezone "0 or 1" :vtodo "1+"}
    :component {:attach "0+" :attendee "1" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "0 or 1" :due "0 or 1"
                :duration "0 or 1" :exdate "0+" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "0 or 1"
                :rdate "0+" :recurrence-id "0 or 1" :related-to "0+" :request-status "0+"
                :resources "0+" :rrule "0 or 1" :sequence "0 or 1" :status "0 or 1"
                :summary "0 or 1" :uid "1" :url "0 or 1" :valarm "0"}}
   ["REQUEST" :vevent]
   {:calendar  {:vevent "1+" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "0"}
    :component {:attach "0+" :attendee "1+" :categories "0+" :class "0 or 1"
                :comment "0+" :contact "0+" :created "0 or 1" :description "0 or 1"
                :dtend "0 or 1" :dtstamp "1" :dtstart "1" :duration "0 or 1"
                :exdate "0+" :geo "0 or 1" :last-modified "0 or 1" :location "0 or 1"
                :organizer "1" :priority "0 or 1" :rdate "0+" :recurrence-id "0 or 1"
                :related-to "0+" :request-status "0" :resources "0+" :rrule "0 or 1"
                :sequence "0 or 1" :status "0 or 1" :summary "1" :transp "0 or 1"
                :uid "1" :url "0 or 1" :valarm "0+"}}
   ["REQUEST" :vfreebusy]
   {:calendar  {:vevent "0" :vfreebusy "1" :vjournal "0" :vtimezone "0" :vtodo "0"}
    :component {:attendee "1+" :comment "0+" :contact "0 or 1" :dtend "1"
                :dtstamp "1" :dtstart "1" :duration "0" :freebusy "0"
                :organizer "1" :request-status "0" :uid "1" :url "0"
                :valarm "0"}}
   ["REQUEST" :vtodo]
   {:calendar  {:vevent "0" :vfreebusy "0" :vjournal "0" :vtimezone "0+" :vtodo "1+"}
    :component {:attach "0+" :attendee "1+" :categories "0+" :class "0 or 1"
                :comment "0+" :completed "0 or 1" :contact "0+" :created "0 or 1"
                :description "0 or 1" :dtstamp "1" :dtstart "1" :due "0 or 1"
                :duration "0 or 1" :exdate "0+" :geo "0 or 1" :last-modified "0 or 1"
                :location "0 or 1" :organizer "1" :percent-complete "0 or 1" :priority "1"
                :rdate "0+" :recurrence-id "0 or 1" :related-to "0+" :request-status "0"
                :resources "0+" :rrule "0 or 1" :sequence "0 or 1" :status "0 or 1"
                :summary "1" :uid "1" :url "0 or 1" :valarm "0+"}}})
