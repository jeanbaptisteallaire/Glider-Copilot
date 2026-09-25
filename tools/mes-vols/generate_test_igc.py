#!/usr/bin/env python3
"""Génère un vol IGC synthétique déterministe pour les tests GLIDY.

La trace est réaliste dans sa forme, mais ne représente aucun vol réel et ne
possède aucune signature de validation FAI.
"""

from __future__ import annotations

import argparse
import math
import random
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path


@dataclass(frozen=True)
class Fix:
    latitude: float
    longitude: float
    altitude: float


class FlightBuilder:
    def __init__(self, latitude: float, longitude: float, altitude: float, seed: int = 260919):
        self.points = [Fix(latitude, longitude, altitude)]
        self.random = random.Random(seed)

    @property
    def current(self) -> Fix:
        return self.points[-1]

    def glide(self, target_lat: float, target_lon: float, target_alt: float, seconds: int) -> None:
        start = self.current
        bearing_lat = target_lat - start.latitude
        bearing_lon = target_lon - start.longitude
        length = math.hypot(bearing_lat, bearing_lon) or 1.0
        normal_lat = -bearing_lon / length
        normal_lon = bearing_lat / length
        for index in range(1, seconds + 1):
            progress = index / seconds
            easing = progress * progress * (3.0 - 2.0 * progress)
            sway = math.sin(progress * math.pi * 3.0) * 0.0012 * math.sin(math.pi * progress)
            latitude = start.latitude + bearing_lat * easing + normal_lat * sway
            longitude = start.longitude + bearing_lon * easing + normal_lon * sway
            altitude = start.altitude + (target_alt - start.altitude) * progress
            altitude += math.sin(index / 17.0) * 2.0 + self.random.uniform(-0.7, 0.7)
            self.points.append(Fix(latitude, longitude, altitude))

    def thermal(
        self,
        center_lat: float,
        center_lon: float,
        radius_lat: float,
        start_alt: float,
        target_alt: float,
        turns: float,
        seconds: int,
    ) -> None:
        start = self.current
        initial_angle = math.atan2(
            (start.latitude - center_lat) / radius_lat,
            (start.longitude - center_lon) / (radius_lat / math.cos(math.radians(center_lat))),
        )
        radius_lon = radius_lat / math.cos(math.radians(center_lat))
        for index in range(1, seconds + 1):
            progress = index / seconds
            angle = initial_angle + progress * turns * 2.0 * math.pi
            radius_factor = 0.80 + 0.20 * math.sin(progress * math.pi)
            latitude = center_lat + math.sin(angle) * radius_lat * radius_factor
            longitude = center_lon + math.cos(angle) * radius_lon * radius_factor
            altitude = start_alt + (target_alt - start_alt) * progress
            altitude += math.sin(angle * 1.7) * 5.0 + self.random.uniform(-1.0, 1.0)
            self.points.append(Fix(latitude, longitude, altitude))

    def hold(self, seconds: int, target_alt: float | None = None) -> None:
        start = self.current
        end_alt = start.altitude if target_alt is None else target_alt
        for index in range(1, seconds + 1):
            progress = index / seconds
            latitude = start.latitude + math.sin(index / 19.0) * 0.00003
            longitude = start.longitude + math.cos(index / 23.0) * 0.00003
            altitude = start.altitude + (end_alt - start.altitude) * progress
            self.points.append(Fix(latitude, longitude, altitude))


def format_coordinate(value: float, degree_digits: int) -> tuple[str, str]:
    hemisphere = ("N" if value >= 0 else "S") if degree_digits == 2 else ("E" if value >= 0 else "W")
    absolute = abs(value)
    degrees = int(absolute)
    minute_thousandths = round((absolute - degrees) * 60.0 * 1000.0)
    if minute_thousandths >= 60_000:
        degrees += 1
        minute_thousandths = 0
    return f"{degrees:0{degree_digits}d}{minute_thousandths:05d}", hemisphere


def altitude_field(value: float) -> str:
    altitude = round(value)
    if altitude < 0:
        return f"-{abs(altitude):04d}"
    return f"{altitude:05d}"


def build_flight() -> list[Fix]:
    # Aérodrome de Saint-Martin-de-Londres, coordonnées volontairement arrondies.
    flight = FlightBuilder(43.80142, 3.78102, 190.0)
    flight.hold(45, 195.0)
    # Environ 130 m de rayon et 30 à 40 s par tour : valeurs cohérentes
    # pour un planeur en ascendance.
    flight.glide(43.8370, 3.7217, 860.0, 8 * 60)
    flight.thermal(43.8370, 3.7200, 0.0012, flight.current.altitude, 1_720.0, 19.0, 11 * 60)
    flight.glide(43.9780, 3.4817, 980.0, 19 * 60)
    flight.thermal(43.9780, 3.4800, 0.0012, flight.current.altitude, 2_130.0, 22.0, 13 * 60)
    flight.glide(44.0470, 3.7527, 1_080.0, 22 * 60)
    flight.thermal(44.0470, 3.7510, 0.0012, flight.current.altitude, 1_830.0, 17.0, 10 * 60)
    flight.glide(43.8200, 3.8060, 430.0, 22 * 60)
    flight.glide(43.80142, 3.78102, 205.0, 6 * 60)
    flight.hold(75, 190.0)
    return flight.points


def write_igc(destination: Path) -> None:
    flight_date = date(2026, 9, 19)
    started_at = datetime.combine(flight_date, time(9, 12, 0), tzinfo=timezone.utc)
    fixes = build_flight()
    lines = [
        "AXXXGLYGLIDY",
        f"HFDTEDATE:{flight_date:%d%m%y},01",
        "HFPLTPILOTINCHARGE:PILOTE TEST",
        "HFGTYGLIDERTYPE:PLANEUR SYNTHETIQUE",
        "HFGIDGLIDERID:TEST-IGC",
        "HFDTMGPSDATUM:WGS84",
        "HFRFWFIRMWAREVERSION:GLIDY PHASE 3",
        "HFFTYFRTYPE:GLIDY,GENERATEUR DE TEST",
        "LXXXGLIDY TRACE SYNTHETIQUE NON CERTIFIEE - NE REPRESENTE AUCUN VOL REEL",
        "LXXXGLIDY SCENARIO: DECOLLAGE, TROIS ASCENDANCES, TRANSITIONS ET RETOUR TERRAIN",
    ]

    for offset, fix in enumerate(fixes):
        timestamp = started_at + timedelta(seconds=offset)
        latitude, latitude_hemisphere = format_coordinate(fix.latitude, 2)
        longitude, longitude_hemisphere = format_coordinate(fix.longitude, 3)
        gps_altitude = altitude_field(fix.altitude)
        pressure_altitude = altitude_field(fix.altitude - 28.0 + math.sin(offset / 120.0) * 3.0)
        lines.append(
            f"B{timestamp:%H%M%S}{latitude}{latitude_hemisphere}"
            f"{longitude}{longitude_hemisphere}A{pressure_altitude}{gps_altitude}"
        )

    lines.append("LXXXGLIDY FIN DE LA TRACE SYNTHETIQUE")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(lines) + "\n", encoding="ascii")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "destination",
        nargs="?",
        type=Path,
        default=Path("samples/saint-martin-de-londres-vol-synthetique.igc"),
    )
    args = parser.parse_args()
    write_igc(args.destination)
    print(args.destination)


if __name__ == "__main__":
    main()
