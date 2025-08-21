import React from 'react';
import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle,} from '@mui/material';

type Props = {
    open: boolean;
    title: String;
    content: String
    onClose: () => void;
    onConfirm: () => void;
};

export default function ConfirmationDialog({open, title, content, onConfirm, onClose}: Props) {
    return (
        <Dialog open={open} onClose={onClose} aria-labelledby="confirmation-dialog-title">
            <DialogTitle id="confirmation-dialog-title">{title}</DialogTitle>
            <DialogContent>
                <DialogContentText>{content}</DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} color="primary">
                    Cancel
                </Button>
                <Button onClick={onConfirm} color="secondary" autoFocus>
                    Confirm
                </Button>
            </DialogActions>
        </Dialog>
    );
};

