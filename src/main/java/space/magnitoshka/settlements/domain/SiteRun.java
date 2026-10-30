package space.magnitoshka.settlements.domain;

/** One run of identical original blocks, inclusive vertical coordinates. */
public record SiteRun(int bottom,int top,String block) {}
