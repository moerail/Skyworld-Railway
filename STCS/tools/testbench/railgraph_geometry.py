"""Shared RailGraph canvas geometry for the offline testbench and remote dispatcher."""
from __future__ import annotations


def fit_transform(edges, width, height):
    points = [point for edge in edges for point in edge.points]
    if not points:
        return None
    x0, x1 = min(p[1] for p in points), max(p[1] for p in points)
    z0, z1 = min(p[3] for p in points), max(p[3] for p in points)
    scale = min((width - 100) / max(1, x1 - x0), (height - 120) / max(1, z1 - z0))
    return scale, width / 2 - (x0 + x1) / 2 * scale, height / 2 - (z0 + z1) / 2 * scale


def edge_screen_coords(edge, screen, start=0, end=None):
    end = edge.length if end is None else end
    if end <= start:
        return []
    points = [edge.point(start)] + [p[1:] for p in edge.points if start < p[0] < end] + [edge.point(end)]
    return [value for point in points for value in screen(point)]
