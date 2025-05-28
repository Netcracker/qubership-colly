import React from "react";

const LogoutButton = () => {
    const handleLogout = async () => {
        try {
            const response = await fetch("/colly/logout", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
            });
            if (response.ok) {
                const data = await response.json();
                if (data.logoutUrl) {
                    window.location.href = data.logoutUrl;
                } else {
                    window.location.reload();
                }
            } else {
                console.error("Logout failed");
                window.location.reload();
            }
        } catch (error) {
            console.error("Error during logout:", error);
            window.location.reload();
        }
    };

    return <button onClick={handleLogout}>Logout</button>;
};

export default LogoutButton;
