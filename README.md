TripCompass
Description
TripCompass is a travel planning service that integrates Amadeus and Google Maps data. The application exposes a REST API (built with Javalin) that returns information about flights, hotels, and nearby airports.
Requirements
Java 17 or higher (developed and tested with JDK 23)


Node.js 18 or higher (for frontend dependencies)


Maven and npm installed on your system


API Keys Setup
This project requires keys for Amadeus and Google Maps. You can define them in a .env file at the project root:
env
CopyEdit
AMADEUS_API_KEY=your_key
AMADEUS_API_SECRET=your_secret
GOOGLE_MAPS_API_KEY=your_key

Build & Run
Install JavaScript dependencies:

bash
CopyEdit
npm install


Compile and start the Javalin server:

bash
CopyEdit
mvn exec:java
The application will listen on port 7000.


Example Endpoints
/trip-info
Returns combined information about hotels and flights. Results include full airline names. The optional `maxFlights` parameter (default 10) limits the number of flight offers.bash
CopyEdit
curl "http://localhost:7000/trip-info?lat=48.8566&lng=2.3522&origin=FCO&checkInDate=2025-12-01&adults=2&roomQuantity=1&maxFlights=5"
/nearby-airports
Lists airports close to the given coordinates.
bash
CopyEdit
curl "http://localhost:7000/nearby-airports?lat=48.8566&lng=2.3522&limit=3"

/search/flights
Searches flight offers between two airports. Supports an optional `limit` query parameter (default 10) to restrict the number of returned offers. Airline codes are resolved to full names.bash
CopyEdit
curl "http://localhost:7000/search/flights?origin=FCO&destination=JFK&departureDate=2025-12-01&returnDate=2025-12-10&adults=1&limit=5"

/search/nearby
Finds hotel offers near a location.
bash
CopyEdit
curl "http://localhost:7000/search/nearby?lat=48.8566&lng=2.3522&checkInDate=2025-12-01&adults=2&roomQuantity=1"

Notes on API Keys
Keys are loaded at startup via dotenv. Make sure the .env file exists at the project root, or that the environment variables are set, before starting the server
