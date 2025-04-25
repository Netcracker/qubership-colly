export type Environment = {
    id: number;
    name: string;
    namespaces: { name: string }[];
    cluster: { name: string };
    owner: string;
    status: EnvironmentStatus;
    labels: string[];
    description: string;
};
export type EnvironmentStatus = "IN_USE" | "RESERVED" | "FREE" | "MIGRATING";

export const ALL_STATUSES: EnvironmentStatus[] = ["FREE", "IN_USE", "MIGRATING", "RESERVED"]

export const STATUS_MAPPING = {
    IN_USE: "In Use",
    FREE: "Free",
    MIGRATING: "Migrating",
    RESERVED: "Reserved"
};
